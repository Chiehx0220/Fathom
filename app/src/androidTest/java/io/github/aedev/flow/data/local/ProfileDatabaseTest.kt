package io.github.aedev.flow.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.data.local.entity.DownloadEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Every profile opens its own file of the same AppDatabase class, so no profile ever needs a schema change or a
 * migration of its own. The test runner's Application skips FlowApplication, so the active profile is read lazily
 * from the registry, as in a restore.
 */
@RunWith(AndroidJUnit4::class)
class ProfileDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.filesDir, "profile_library/profile-database-test")
    private val path = File(root, "databases/${AppDatabase.DEFAULT_NAME}").apply { parentFile?.mkdirs() }.absolutePath

    @After
    fun tearDown() {
        root.deleteRecursively()
        root.parentFile?.delete()
    }

    @Test
    fun withDefaultRunningBothHandlesAreOneDatabase() {
        assertSame(AppDatabase.device(context), AppDatabase.profile(context))
    }

    @Test
    fun anotherProfilesFileOpensAtTheCurrentVersionWithEmptyTables() {
        val database = AppDatabase.open(context, path)
        try {
            val sqlite = database.openHelper.readableDatabase
            assertEquals(33, sqlite.version)
            listOf("watch_history", "playlists", "videos", "notes", "downloads", "sync_log").forEach { table ->
                sqlite.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(table, 0, cursor.getInt(0))
                }
            }
            assertTrue(File(path).exists())
        } finally {
            database.close()
        }
    }

    @Test
    fun anotherProfilesFileKeepsItsRowsWhenOpenedAgain() =
        runTest {
            AppDatabase.open(context, path).run {
                downloadDao().insertDownload(DownloadEntity(videoId = "v1", title = "Kept", uploader = "Uploader"))
                close()
            }

            val reopened = AppDatabase.open(context, path)
            try {
                assertEquals(
                    "Kept",
                    reopened
                        .downloadDao()
                        .getDownloadWithItems("v1")
                        ?.download
                        ?.title,
                )
            } finally {
                reopened.close()
            }
        }
}
