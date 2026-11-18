package io.github.aedev.flow.localserver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.video.storage.DownloadCovers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class DownloadCoverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a cover the app saves is the one the route serves`() {
        val path = DownloadCovers.save(context, "BV1g2JX6HEHX?p=1", byteArrayOf(1, 2, 3))!!
        val name = File(path).name

        assertThat(coverRoute(path)).isEqualTo("/download-cover?f=$name")
        assertThat(coverRoute("file://$path")).isEqualTo("/download-cover?f=$name")
        assertThat(savedCover(context.filesDir, name)?.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `remote pictures and other local files keep their address`() {
        assertThat(coverRoute("https://i.ytimg.com/vi/x/hqdefault.jpg")).isEqualTo("https://i.ytimg.com/vi/x/hqdefault.jpg")
        assertThat(coverRoute("/data/user/0/app/files/secret.jpg")).isEqualTo("/data/user/0/app/files/secret.jpg")
        assertThat(coverRoute("/data/user/0/app/files/download_covers/../secret.jpg")).doesNotContain("download-cover")
        assertThat(coverRoute("")).isEmpty()
    }

    @Test
    fun `only a plain jpg name is served`() {
        val dir = File(context.filesDir, COVER_DIRECTORY).apply { mkdirs() }
        File(dir, "ok.jpg").writeText("x")
        File(context.filesDir, "secret.jpg").writeText("x")

        assertThat(savedCover(context.filesDir, "ok.jpg")).isNotNull()
        assertThat(savedCover(context.filesDir, "../secret.jpg")).isNull()
        assertThat(savedCover(context.filesDir, "sub/ok.jpg")).isNull()
        assertThat(savedCover(context.filesDir, "ok.png")).isNull()
        assertThat(savedCover(context.filesDir, "missing.jpg")).isNull()
        assertThat(savedCover(context.filesDir, null)).isNull()
    }

    @Test
    fun `the cover route is for the server's own page only`() {
        assertThat(OWN_PAGE_PATHS).contains("/download-cover")
    }
}
