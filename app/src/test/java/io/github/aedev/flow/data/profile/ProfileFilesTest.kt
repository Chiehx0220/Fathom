package io.github.aedev.flow.data.profile

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class ProfileFilesTest {
    private val filesDir = File("/data/user/0/app/files")
    private val noBackupFilesDir = File("/data/user/0/app/no_backup")
    private val default = RunningProfile(id = "d", isDefault = true)
    private val study = RunningProfile(id = "0b6f0d3c-2f1e-4b8a-9c5d-7e6f5a4b3c2d", isDefault = false)

    private fun path(
        profile: RunningProfile,
        scope: StoreScope,
    ) = ProfileFiles.preferences(filesDir, noBackupFilesDir, profile, scope, "player_preferences").path

    @Test
    fun `Default keeps the path every store had before profiles, whatever its scope`() {
        StoreScope.entries.forEach { scope ->
            assertThat(path(default, scope)).isEqualTo("/data/user/0/app/files/datastore/player_preferences.preferences_pb")
        }
    }

    @Test
    fun `a Default that could not be read still keeps those paths`() {
        assertThat(path(RunningProfile(id = "", isDefault = true), StoreScope.PROFILE_PRIVATE))
            .isEqualTo("/data/user/0/app/files/datastore/player_preferences.preferences_pb")
    }

    @Test
    fun `device stores are shared by every profile`() {
        assertThat(path(study, StoreScope.DEVICE)).isEqualTo(path(default, StoreScope.DEVICE))
    }

    @Test
    fun `another profile keeps each class in its own folder`() {
        val id = study.id
        assertThat(path(study, StoreScope.PROFILE_SETTINGS))
            .isEqualTo("/data/user/0/app/files/profile_settings/$id/datastore/player_preferences.preferences_pb")
        assertThat(path(study, StoreScope.PROFILE_LIBRARY))
            .isEqualTo("/data/user/0/app/files/profile_library/$id/datastore/player_preferences.preferences_pb")
        assertThat(path(study, StoreScope.PROFILE_PRIVATE))
            .isEqualTo("/data/user/0/app/no_backup/profiles/$id/datastore/player_preferences.preferences_pb")
    }

    @Test
    fun `a Default-only store touched from another profile never reaches Default's file`() {
        assertThat(path(study, StoreScope.DEFAULT_ONLY)).isNotEqualTo(path(default, StoreScope.DEFAULT_ONLY))
        assertThat(path(study, StoreScope.DEFAULT_ONLY)).startsWith("/data/user/0/app/no_backup/profiles/${study.id}/")
    }

    @Test
    fun `Default keeps its no_backup files where they are, another profile in its private folder`() {
        assertThat(ProfileFiles.noBackupRoot(noBackupFilesDir, default).path).isEqualTo("/data/user/0/app/no_backup")
        assertThat(ProfileFiles.noBackupRoot(noBackupFilesDir, study).path).isEqualTo("/data/user/0/app/no_backup/profiles/${study.id}")
    }

    @Test
    fun `a legacy file only Default has is never looked for in Default's place from another profile`() {
        val defaults = File(ProfileFiles.root(filesDir, noBackupFilesDir, default, StoreScope.DEFAULT_ONLY), "user_neuro_brain.json")
        val other = File(ProfileFiles.root(filesDir, noBackupFilesDir, study, StoreScope.DEFAULT_ONLY), "user_neuro_brain.json")

        assertThat(defaults.path).isEqualTo("/data/user/0/app/files/user_neuro_brain.json")
        assertThat(other.path).startsWith("/data/user/0/app/no_backup/profiles/${study.id}/")
    }

    @Test
    fun `Default's database stays where Room has always kept it, another profile's sits in its library folder`() {
        assertThat(ProfileFiles.database(filesDir, noBackupFilesDir, default, "flow_database")).isNull()
        assertThat(ProfileFiles.database(filesDir, noBackupFilesDir, study, "flow_database")?.path)
            .isEqualTo("/data/user/0/app/files/profile_library/${study.id}/databases/flow_database")
    }

    @Test
    fun `Default keeps its SharedPreferences names, another profile gets its own`() {
        assertThat(ProfileFiles.sharedPreferencesName(default, "youtube_identity")).isEqualTo("youtube_identity")
        assertThat(ProfileFiles.sharedPreferencesName(study, "youtube_identity")).isEqualTo("youtube_identity_${study.id}")
        assertThrows(IllegalArgumentException::class.java) {
            ProfileFiles.sharedPreferencesName(RunningProfile(id = "../x", isDefault = false), "youtube_identity")
        }
    }

    @Test
    fun `an id that could leave its folder is refused`() {
        listOf("../d", "a/b", "", ".").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) {
                path(RunningProfile(id = id, isDefault = false), StoreScope.PROFILE_LIBRARY)
            }
        }
    }
}
