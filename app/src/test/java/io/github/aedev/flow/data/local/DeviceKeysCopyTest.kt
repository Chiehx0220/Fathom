package io.github.aedev.flow.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory

class DeviceKeysCopyTest {
    private val dir = createTempDirectory("device-keys").toFile()
    private val playerFile = File(dir, "player_preferences.preferences_pb")
    private val flowFile = File(dir, "flow_preferences.preferences_pb")
    private val deviceFile = File(dir, "device_preferences.preferences_pb")
    private val jobs = mutableListOf<Job>()

    private val downloadThreads = intPreferencesKey("download_threads")
    private val proxyHost = stringPreferencesKey("proxy_host")
    private val swipeGestures = booleanPreferencesKey("swipe_gestures_enabled")
    private val speedSwipeStep = floatPreferencesKey("speed_swipe_step")
    private val donationTime = longPreferencesKey("donation_first_launch_time")
    private val hiddenIds = stringSetPreferencesKey("unplayable_video_ids")
    private val sponsorColor = stringPreferencesKey("sb_color_sponsor")
    private val lastUpdateCheck = stringPreferencesKey("last_update_check")
    private val themeMode = stringPreferencesKey("theme_mode")

    private val playerNames =
        setOf(
            "download_threads",
            "proxy_host",
            "swipe_gestures_enabled",
            "speed_swipe_step",
            "donation_first_launch_time",
            "unplayable_video_ids",
        )
    private val flowNames = setOf("last_update_check")

    @After
    fun cleanUp() =
        runBlocking<Unit> {
            jobs.forEach { it.cancelAndJoin() }
            dir.deleteRecursively()
        }

    private fun store(
        file: File,
        migrations: List<DeviceKeysCopy> = emptyList(),
    ): DataStore<Preferences> {
        val job = Job().also(jobs::add)
        return PreferenceDataStoreFactory.create(
            migrations = migrations,
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        )
    }

    private fun copy(
        player: DataStore<Preferences>,
        flow: DataStore<Preferences>,
    ) = DeviceKeysCopy(
        listOf(
            DeviceKeysCopy.Source(player, playerNames, playerFile),
            DeviceKeysCopy.Source(flow, flowNames, flowFile),
        ),
    )

    @Test
    fun `device keys are copied with their types and profile keys stay behind`() =
        runBlocking<Unit> {
            val player = store(playerFile)
            val flow = store(flowFile)
            player.edit {
                it[downloadThreads] = 4
                it[proxyHost] = "10.0.0.2"
                it[swipeGestures] = false
                it[speedSwipeStep] = 0.25f
                it[donationTime] = 1_700_000_000_000L
                it[hiddenIds] = setOf("a", "b")
                it[sponsorColor] = "red"
            }
            flow.edit {
                it[lastUpdateCheck] = "123"
                it[themeMode] = "DARK"
            }

            val device = store(deviceFile, listOf(copy(player, flow))).data.first()

            assertThat(device[downloadThreads]).isEqualTo(4)
            assertThat(device[proxyHost]).isEqualTo("10.0.0.2")
            assertThat(device[swipeGestures]).isFalse()
            assertThat(device[speedSwipeStep]).isEqualTo(0.25f)
            assertThat(device[donationTime]).isEqualTo(1_700_000_000_000L)
            assertThat(device[hiddenIds]).containsExactly("a", "b")
            assertThat(device[lastUpdateCheck]).isEqualTo("123")
            assertThat(device[sponsorColor]).isNull()
            assertThat(device[themeMode]).isNull()
        }

    @Test
    fun `the sources keep every value`() =
        runBlocking<Unit> {
            val player = store(playerFile)
            val flow = store(flowFile)
            player.edit {
                it[downloadThreads] = 4
                it[sponsorColor] = "red"
            }

            store(deviceFile, listOf(copy(player, flow))).data.first()

            assertThat(player.data.first().asMap()).containsExactly(downloadThreads, 4, sponsorColor, "red")
        }

    @Test
    fun `the copy runs once, so a later change in the old file never comes back`() =
        runBlocking<Unit> {
            val player = store(playerFile)
            val flow = store(flowFile)
            player.edit { it[downloadThreads] = 4 }
            val device = store(deviceFile, listOf(copy(player, flow)))
            device.data.first()
            device.edit { it.remove(downloadThreads) }
            player.edit { it[downloadThreads] = 2 }

            val again = copy(player, flow)

            assertThat(again.shouldMigrate(device.data.first())).isFalse()
            assertThat(device.data.first()[downloadThreads]).isNull()
        }

    @Test
    fun `nothing set copies nothing but still finishes`() =
        runBlocking<Unit> {
            val device = store(deviceFile, listOf(copy(store(playerFile), store(flowFile)))).data.first()

            assertThat(device.asMap()).containsExactly(DeviceKeysCopy.COPIED, true)
        }

    @Test
    fun `a source that reads back empty while its file has data is not taken as empty`() {
        playerFile.writeBytes(byteArrayOf(1, 2, 3))
        val unreadable =
            object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flowOf(emptyPreferences())

                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences) = error("read only")
            }
        val migration = DeviceKeysCopy(listOf(DeviceKeysCopy.Source(unreadable, playerNames, playerFile)))

        assertThrows(IOException::class.java) { runBlocking { migration.migrate(emptyPreferences()) } }
    }
}
