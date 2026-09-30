package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HlsRelayTest {
    private val base = "https://d1--cn-gotcha208.bilivideo.com/live-bvc/123/index.m3u8?expires=9"

    @Test
    fun `every address of a playlist goes through the relay`() {
        val playlist =
            """
            #EXTM3U
            #EXT-X-MAP:URI="h123.m4s"
            #EXTINF:1.0,
            1.m4s?x=1
            """.trimIndent()

        val out = rewriteHlsPlaylist(playlist, base).lines()

        assertThat(out[1]).isEqualTo(
            "#EXT-X-MAP:URI=\"" + hlsRelayPath("https://d1--cn-gotcha208.bilivideo.com/live-bvc/123/h123.m4s") + "\"",
        )
        assertThat(out[3]).isEqualTo(hlsRelayPath("https://d1--cn-gotcha208.bilivideo.com/live-bvc/123/1.m4s?x=1"))
    }

    @Test
    fun `an absolute address is kept and the content steering tag is dropped`() {
        val playlist =
            """
            #EXTM3U
            #EXT-X-CONTENT-STEERING:SERVER-URI="https://steer.example/x",PATHWAY-ID="a"
            #EXT-X-STREAM-INF:BANDWIDTH=1000
            https://other.bilivideo.com/live/a.m3u8
            """.trimIndent()

        val out = rewriteHlsPlaylist(playlist, base)

        assertThat(out).doesNotContain("STEERING")
        assertThat(out).contains(hlsRelayPath("https://other.bilivideo.com/live/a.m3u8"))
    }
}
