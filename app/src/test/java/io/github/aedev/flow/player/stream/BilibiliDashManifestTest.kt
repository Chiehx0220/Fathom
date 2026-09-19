package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BilibiliStreamFormat
import org.junit.Test

class BilibiliDashManifestTest {
    private fun format(
        initRange: LongRange? = 0L..999L,
        indexRange: LongRange? = 1000L..1999L,
        url: String = "https://upos-sz-mirrorcosov.bilivideo.com/v/1.m4s?a=1&b=2",
    ) = BilibiliStreamFormat(
        id = 32,
        url = url,
        backupUrls = emptyList(),
        codecs = "avc1.640033",
        bandwidth = 286_940,
        width = 852,
        height = 480,
        frameRate = "30.000",
        initRange = initRange,
        indexRange = indexRange,
    )

    @Test
    fun `a video with ranges gets a single-representation manifest that points at its url`() {
        val stream = BilibiliStreamBridge.convertVideoFormats("BV1", listOf(format())).single()
        val manifest = BilibiliDashManifest.forVideo(stream, durationSeconds = 177)!!
        assertThat(manifest).contains("<SegmentBase indexRange=\"1000-1999\">")
        assertThat(manifest).contains("<Initialization range=\"0-999\"/>")
        assertThat(manifest).contains("mediaPresentationDuration=\"PT177S\"")
        assertThat(manifest).contains("codecs=\"avc1.640033\"")
        assertThat(manifest).contains("width=\"852\"")
    }

    @Test
    fun `the url is xml-escaped so a query string cannot break the manifest`() {
        val stream = BilibiliStreamBridge.convertVideoFormats("BV1", listOf(format())).single()
        val manifest = BilibiliDashManifest.forVideo(stream, 10)!!
        assertThat(manifest).contains("a=1&amp;b=2")
        assertThat(manifest).doesNotContain("a=1&b=2")
    }

    @Test
    fun `a stream without ranges gets no manifest and falls back to progressive`() {
        val stream =
            BilibiliStreamBridge
                .convertVideoFormats("BV1", listOf(format(initRange = null, indexRange = null)))
                .single()
        assertThat(BilibiliDashManifest.forVideo(stream, 177)).isNull()
    }

    @Test
    fun `audio keeps its full codec string in the manifest`() {
        val audio =
            BilibiliStreamFormat(
                id = 30280,
                url = "https://upos-hz-mirrorakam.akamaized.net/a/1.m4s",
                backupUrls = emptyList(),
                codecs = "mp4a.40.5",
                bandwidth = 49_320,
                width = 0,
                height = 0,
                frameRate = null,
                initRange = 0L..99L,
                indexRange = 100L..199L,
            )
        val stream = BilibiliStreamBridge.convertAudioFormats("BV1", listOf(audio)).single()
        val manifest = BilibiliDashManifest.forAudio(stream, 177)!!
        assertThat(manifest).contains("codecs=\"mp4a.40.5\"")
        assertThat(manifest).contains("contentType=\"audio\"")
    }
}
