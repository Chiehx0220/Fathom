package io.github.aedev.flow.data.account

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class YouTubeWatchReporterTest {
    private val base = "https://s.youtube.com/api/stats/playback?ns=yt&el=detailpage&docid=abc&ver=1&cpn=old&c=TVHTML5"

    @Test
    fun `a nonce is sixteen url-safe characters`() {
        val cpn = newClientPlaybackNonce(Random(1))

        assertThat(cpn).hasLength(16)
        assertThat(cpn).matches("[A-Za-z0-9_-]{16}")
    }

    @Test
    fun `tracking params replace the ones the base url carried`() {
        val url = trackingUrl(base, playbackParams("NEWNONCE0000000A"))

        assertThat(url).contains("cpn=NEWNONCE0000000A")
        assertThat(url).doesNotContain("cpn=old")
        assertThat(url).contains("ver=2")
        assertThat(url).doesNotContain("ver=1")
        assertThat(url).contains("docid=abc")
        assertThat(url).startsWith("https://s.youtube.com/api/stats/playback")
    }

    @Test
    fun `a watch-time ping covers the segment since the last one`() {
        val params = watchtimeParams("n", startMs = 30_000L, positionMs = 61_500L, final = false)

        assertThat(params["st"]).isEqualTo("30.0")
        assertThat(params["et"]).isEqualTo("61.5")
        assertThat(params["cmt"]).isEqualTo("61.5")
        assertThat(params).doesNotContainKey("final")
    }

    @Test
    fun `the last watch-time ping is marked final`() {
        assertThat(watchtimeParams("n", 0L, 10_000L, final = true)["final"]).isEqualTo("1")
    }
}
