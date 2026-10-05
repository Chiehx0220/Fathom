package io.github.aedev.flow.player.datasource

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class BilibiliRoutingDataSourceTest {
    private val youTubeUrl = "https://rr1---sn-abc.googlevideo.com/videoplayback?id=1"
    private val bilibiliUrl = "https://upos-sz-mirror.bilivideo.com/upgcxcode/video.m4s"

    private fun source(
        length: Long,
        uri: String,
    ): HttpDataSource =
        mockk(relaxed = true) {
            every { open(any()) } returns length
            every { this@mockk.uri } returns Uri.parse(uri)
            every { responseCode } returns 206
            every { read(any(), any(), any()) } returns 7
        }

    private class Routed(
        val youTube: HttpDataSource,
        val bilibili: HttpDataSource,
        var bilibiliOpened: Int = 0,
    ) {
        val router =
            BilibiliRoutingDataSource(youTube) {
                bilibiliOpened++
                bilibili
            }
    }

    private fun routed() = Routed(source(100, "https://youtube.test/"), source(200, "https://bilibili.test/"))

    @Test
    fun `a YouTube url goes to the YouTube source and never builds a Bilibili one`() {
        val r = routed()

        assertThat(r.router.open(DataSpec(Uri.parse(youTubeUrl)))).isEqualTo(100)

        verify(exactly = 1) { r.youTube.open(any()) }
        assertThat(r.bilibiliOpened).isEqualTo(0)
    }

    @Test
    fun `a Bilibili CDN url goes to a Bilibili source and leaves the YouTube one alone`() {
        val r = routed()

        assertThat(r.router.open(DataSpec(Uri.parse(bilibiliUrl)))).isEqualTo(200)

        verify(exactly = 1) { r.bilibili.open(any()) }
        verify(exactly = 0) { r.youTube.open(any()) }
    }

    @Test
    fun `reads, uri and response follow the source that was opened`() {
        val r = routed()
        r.router.open(DataSpec(Uri.parse(bilibiliUrl)))

        assertThat(r.router.read(ByteArray(8), 0, 8)).isEqualTo(7)
        assertThat(r.router.uri.toString()).isEqualTo("https://bilibili.test/")
        assertThat(r.router.responseCode).isEqualTo(206)
        verify(exactly = 0) { r.youTube.read(any(), any(), any()) }
    }

    @Test
    fun `one instance can serve a YouTube range and then a Bilibili one`() {
        val r = routed()

        r.router.open(DataSpec(Uri.parse(youTubeUrl)))
        r.router.close()
        r.router.open(DataSpec(Uri.parse(bilibiliUrl)))

        verify(exactly = 1) { r.youTube.open(any()) }
        verify(exactly = 1) { r.bilibili.open(any()) }
        assertThat(r.router.read(ByteArray(8), 0, 8)).isEqualTo(7)
    }

    @Test
    fun `close closes only the source in use and leaves nothing to read`() {
        val r = routed()
        r.router.open(DataSpec(Uri.parse(youTubeUrl)))

        r.router.close()

        verify(exactly = 1) { r.youTube.close() }
        verify(exactly = 0) { r.bilibili.close() }
        assertThat(r.router.read(ByteArray(8), 0, 8)).isEqualTo(C.RESULT_END_OF_INPUT)
        assertThat(r.router.uri).isNull()
        assertThat(r.router.responseCode).isEqualTo(-1)
    }

    @Test
    fun `before anything is opened there is nothing to read`() {
        val r = routed()

        assertThat(r.router.read(ByteArray(8), 0, 8)).isEqualTo(C.RESULT_END_OF_INPUT)
        assertThat(r.router.responseHeaders).isEmpty()
    }
}
