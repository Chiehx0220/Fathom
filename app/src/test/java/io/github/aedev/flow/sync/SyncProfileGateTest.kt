package io.github.aedev.flow.sync

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.data.profile.ActiveProfile
import io.github.aedev.flow.data.profile.Profile
import io.github.aedev.flow.data.profile.ProfileRegistryState
import io.github.aedev.flow.data.profile.ProfileRepository
import io.github.aedev.flow.data.profile.RunningProfile
import io.github.aedev.flow.data.profile.SyncProfile
import io.github.aedev.flow.sync.protocol.SyncRole
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

class SyncProfileGateTest {
    private val context: Context = mockk(relaxed = true)
    private val registry =
        ProfileRegistryState(
            profiles = listOf(Profile(id = "d", name = ""), Profile(id = "b", name = "Study")),
            defaultId = "d",
            activeId = "b",
        )
    private val profiles: ProfileRepository = mockk { every { state } returns flowOf(registry) }

    @Before
    fun setUp() {
        every { context.getString(R.string.profile_default_name) } returns "Default"
        every { context.getString(R.string.sync_default_only_title, any()) } answers
            { "Switch to ${(it.invocation.args[1] as Array<*>)[0]} to sync" }
        mockkObject(ActiveProfile)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `sync names the profile it runs in, falling back to Default when it has no name`() {
        assertThat(SyncProfile.name(registry, context)).isEqualTo("Default")
        assertThat(SyncProfile.name(registry.copy(profiles = listOf(Profile(id = "d", name = "Home"))), context)).isEqualTo("Home")
    }

    @Test
    fun `outside Default a session never starts, whoever asks`() =
        runBlocking {
            every { ActiveProfile.get(any()) } returns RunningProfile(id = "b", isDefault = false)
            val manager = SyncManager(context, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), profiles)

            manager.host(SyncRole.SENDER, emptyList())
            val hosted = withTimeout(2_000) { manager.state.first { it is SyncState.Failed } }
            withTimeout(2_000) { while (manager.isBusy) delay(10) }
            manager.reset()
            assertThat(manager.state.value).isEqualTo(SyncState.Idle)
            manager.join(SyncRole.RECEIVER, "qr", emptyList())
            val joined = withTimeout(2_000) { manager.state.first { it is SyncState.Failed } }

            assertThat(hosted).isEqualTo(SyncState.Failed("Switch to Default to sync"))
            assertThat(joined).isEqualTo(SyncState.Failed("Switch to Default to sync"))
        }
}
