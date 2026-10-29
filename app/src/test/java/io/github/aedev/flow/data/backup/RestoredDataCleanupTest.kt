package io.github.aedev.flow.data.backup

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.profile.ProfileFolders
import org.junit.After
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class RestoredDataCleanupTest {
    private val dataDir = createTempDirectory("restored-profiles").toFile()
    private val filesDir = File(dataDir, "files")
    private val noBackupFilesDir = File(dataDir, "no_backup")
    private val sharedPrefsDir = File(dataDir, "shared_prefs")
    private val folders = ProfileFolders(filesDir, noBackupFilesDir, dataDir)

    @After
    fun cleanUp() {
        dataDir.deleteRecursively()
    }

    private fun file(
        root: File,
        path: String,
    ) = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText("x")
    }

    @Test
    fun `folders and identities of profiles the registry no longer lists are removed`() {
        val kept =
            listOf(
                file(filesDir, "profile_settings/study/datastore/player_preferences.preferences_pb"),
                file(filesDir, "profile_library/study/databases/flow_database"),
                file(noBackupFilesDir, "profiles/study/datastore/music_queue.preferences_pb"),
                file(sharedPrefsDir, "youtube_identity_study.xml"),
            )
        file(filesDir, "profile_settings/gone/datastore/player_preferences.preferences_pb")
        file(filesDir, "profile_library/gone/databases/flow_database")
        file(noBackupFilesDir, "profiles/gone/datastore/music_queue.preferences_pb")
        file(sharedPrefsDir, "youtube_identity_gone.xml")

        val removed = RestoredDataCleanup.deleteUnlistedProfiles(folders, setOf("default", "study"))

        assertThat(removed).containsExactly("gone", "gone", "gone", "youtube_identity_gone.xml")
        kept.forEach { assertThat(it.exists()).isTrue() }
        assertThat(File(filesDir, "profile_settings/gone").exists()).isFalse()
        assertThat(File(noBackupFilesDir, "profiles/gone").exists()).isFalse()
    }

    @Test
    fun `Default's files are never touched`() {
        val defaults =
            listOf(
                file(filesDir, "datastore/player_preferences.preferences_pb"),
                file(noBackupFilesDir, "neuro/home_snapshots/home_1.json.gz"),
                file(sharedPrefsDir, "youtube_identity.xml"),
                file(sharedPrefsDir, "flow_language_prefs.xml"),
            )

        val removed = RestoredDataCleanup.deleteUnlistedProfiles(folders, setOf("default"))

        assertThat(removed).isEmpty()
        defaults.forEach { assertThat(it.exists()).isTrue() }
    }

    @Test
    fun `a restore with no profile folders removes nothing`() {
        assertThat(RestoredDataCleanup.deleteUnlistedProfiles(folders, emptySet())).isEmpty()
    }
}
