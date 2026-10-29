package io.github.aedev.flow.data.profile

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ProfileFoldersTest {
    private val dataDir = createTempDirectory("profile-folders").toFile()
    private val filesDir = File(dataDir, "files")
    private val noBackupFilesDir = File(dataDir, "no_backup")
    private val folders = ProfileFolders(filesDir, noBackupFilesDir, dataDir)
    private val study = "0b6f0d3c-2f1e-4b8a-9c5d-7e6f5a4b3c2d"
    private val music = "9a8b7c6d-0000-4000-8000-000000000001"
    private val photoOnly = "5c4d3e2f-1111-4aaa-8bbb-000000000002"

    @After
    fun cleanUp() {
        dataDir.deleteRecursively()
    }

    private fun file(path: String) =
        File(dataDir, path).apply {
            parentFile!!.mkdirs()
            writeText("x")
        }

    private fun profileFiles(id: String) =
        listOf(
            file("files/profile_settings/$id/datastore/player_preferences.preferences_pb"),
            file("files/profile_library/$id/databases/flow_database"),
            file("no_backup/profiles/$id/datastore/flow_neuro_brain_v10.json"),
            file("app_webview_$id/Default/Cookies"),
            file("shared_prefs/youtube_identity_$id.xml"),
            file("shared_prefs/music_home_cache_prefs_$id.xml"),
            file("files/profile_avatars/$id.webp"),
        )

    private val defaultFiles
        get() =
            listOf(
                file("files/datastore/player_preferences.preferences_pb"),
                file("databases/flow_database"),
                file("no_backup/neuro/home_snapshots/home_1.json.gz"),
                file("app_webview/Default/Cookies"),
                file("shared_prefs/youtube_identity.xml"),
                file("shared_prefs/music_home_cache_prefs.xml"),
            )

    @Test
    fun `deleting a profile removes exactly its files`() {
        val defaults = defaultFiles
        val others = profileFiles(music)
        profileFiles(study)

        val removed = folders.delete(study)

        assertThat(removed).hasSize(7)
        assertThat(folders.of(study).none(File::exists)).isTrue()
        (defaults + others).forEach { assertThat(it.exists()).isTrue() }
    }

    @Test
    fun `the ids on disk come from folders and per-profile preferences`() {
        defaultFiles
        profileFiles(study)
        file("shared_prefs/youtube_identity_$music.xml")
        file("files/profile_avatars/$photoOnly.webp")

        assertThat(folders.idsOnDisk()).containsExactly(study, music, photoOnly)
    }

    @Test
    fun `an id that could leave its folder is refused`() {
        listOf("..", "../files", "a/b", "").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { folders.delete(id) }
        }
    }
}
