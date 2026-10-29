package io.github.aedev.flow.data.local

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.io.File

/** NewPipe/PipePipe export keeps each stream's service in `service_id`; both services must survive import. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class BackupNewPipeImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newPipeDatabase(): Uri {
        val file = File(context.cacheDir, "newpipe_fixture.db").apply { delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE streams (uid INTEGER PRIMARY KEY, service_id INTEGER, url TEXT, title TEXT, duration INTEGER, " +
                    "uploader TEXT, uploader_url TEXT, thumbnail_url TEXT)",
            )
            db.execSQL("CREATE TABLE stream_history (stream_id INTEGER, access_date INTEGER)")
            db.execSQL("CREATE TABLE stream_state (stream_id INTEGER, progress_time INTEGER)")
            db.execSQL("CREATE TABLE playlists (uid INTEGER PRIMARY KEY, name TEXT)")
            db.execSQL("CREATE TABLE playlist_stream_join (playlist_id INTEGER, stream_id INTEGER, join_index INTEGER)")
            db.execSQL(
                "INSERT INTO streams VALUES (1, 0, 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'Rick', 213, " +
                    "'Rick Astley', 'https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw', '')",
            )
            db.execSQL(
                "INSERT INTO streams VALUES (2, 5, 'https://www.bilibili.com/video/BV1xx411c7mD', 'Bili', 100, " +
                    "'Up', 'https://space.bilibili.com/12345', 'https://i0.hdslb.com/cover.jpg')",
            )
            db.execSQL(
                "INSERT INTO streams VALUES (3, 5, 'https://www.bilibili.com/video/BV1yy411c7mE', 'No cover', 100, " +
                    "'Up', 'https://space.bilibili.com/12345', '')",
            )
            db.execSQL("INSERT INTO stream_history VALUES (1, 1000), (2, 2000), (3, 3000)")
            db.execSQL("INSERT INTO playlists VALUES (1, 'Mixed')")
            db.execSQL("INSERT INTO playlist_stream_join VALUES (1, 1, 0), (1, 2, 1), (1, 3, 2)")
        }
        return Uri.fromFile(file)
    }

    @Test
    fun `watch history keeps each stream's service`() =
        runBlocking {
            val repository = BackupRepository(context)

            val result = repository.importNewPipeWatchHistory(newPipeDatabase())

            assertThat(result.getOrNull()).isEqualTo(3)
            val byId =
                ViewHistory
                    .getInstance(context)
                    .getAllHistory()
                    .first()
                    .associateBy { it.videoId }
            assertThat(byId.getValue("dQw4w9WgXcQ").serviceId).isEqualTo(0)
            assertThat(byId.getValue("dQw4w9WgXcQ").channelId).isEqualTo("UCuAXFkgsw1L7xaCfnd5JJOw")
            assertThat(byId.getValue("BV1xx411c7mD").serviceId).isEqualTo(BILIBILI_SERVICE_ID)
            assertThat(byId.getValue("BV1xx411c7mD").channelId).isEqualTo("12345")
            assertThat(byId.getValue("BV1xx411c7mD").thumbnailUrl).isEqualTo("https://i0.hdslb.com/cover.jpg")
            // No cover in the export: none invented (not a nonexistent YouTube link).
            assertThat(byId.getValue("BV1yy411c7mE").thumbnailUrl).isEmpty()
        }

    @Test
    fun `playlists keep each stream's service`() =
        runBlocking {
            val repository = BackupRepository(context)

            val result = repository.importNewPipePlaylists(newPipeDatabase())

            assertThat(result.getOrNull()).isEqualTo(3)
            val videos =
                AppDatabase
                    .profile(context)
                    .videoDao()
                    .getAllVideos()
                    .associateBy { it.id }
            assertThat(videos.getValue("dQw4w9WgXcQ").serviceId).isEqualTo(0)
            assertThat(videos.getValue("dQw4w9WgXcQ").thumbnailUrl).contains("dQw4w9WgXcQ")
            assertThat(videos.getValue("BV1xx411c7mD").serviceId).isEqualTo(BILIBILI_SERVICE_ID)
            assertThat(videos.getValue("BV1xx411c7mD").thumbnailUrl).isEmpty()
            assertThat(videos.getValue("BV1yy411c7mE").thumbnailUrl).isEmpty()
        }
}
