package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BilibiliStreamFormat
import org.junit.Test

class BilibiliStreamBridgeTest {
    private fun video(
        qn: Int,
        width: Int,
        height: Int,
        codecs: String = "avc1.640033",
        frameRate: String? = "30.000",
    ) = BilibiliStreamFormat(
        id = qn,
        url = "https://upos-sz-mirrorcosov.bilivideo.com/v/$qn.m4s",
        backupUrls = emptyList(),
        codecs = codecs,
        bandwidth = 1_000_000,
        width = width,
        height = height,
        frameRate = frameRate,
        initRange = 0L..999L,
        indexRange = 1000L..1999L,
    )

    private fun audio(
        qn: Int,
        codecs: String,
        bandwidth: Int = 100_000,
    ) = BilibiliStreamFormat(
        id = qn,
        url = "https://upos-sz-mirrorcosov.bilivideo.com/a/$qn.m4s",
        backupUrls = emptyList(),
        codecs = codecs,
        bandwidth = bandwidth,
        width = 0,
        height = 0,
        frameRate = null,
        initRange = 0L..99L,
        indexRange = 100L..199L,
    )

    @Test
    fun `a portrait clip is labelled by its tier, not its pixel height`() {
        val stream = BilibiliStreamBridge.convertVideoFormats("BV1", listOf(video(qn = 64, width = 720, height = 1280))).single()
        assertThat(stream.resolution).isEqualTo("720p")
        assertThat(VideoCodecUtils.qualityHeightFromStream(stream)).isEqualTo(720)
    }

    @Test
    fun `the high frame rate ladder carries the rate in its label`() {
        val stream =
            BilibiliStreamBridge
                .convertVideoFormats("BV1", listOf(video(qn = 116, width = 1920, height = 1080, frameRate = "60.000")))
                .single()
        assertThat(stream.resolution).isEqualTo("1080p60")
    }

    @Test
    fun `an unknown quality id falls back to the shorter side`() {
        val landscape = BilibiliStreamBridge.convertVideoFormats("BV1", listOf(video(qn = 999, width = 1920, height = 1080))).single()
        val portrait = BilibiliStreamBridge.convertVideoFormats("BV1", listOf(video(qn = 999, width = 1080, height = 1920))).single()
        assertThat(landscape.resolution).isEqualTo("1080p")
        assertThat(portrait.resolution).isEqualTo("1080p")
    }

    @Test
    fun `video streams keep their url, codec and video-only flag`() {
        val stream = BilibiliStreamBridge.convertVideoFormats("BV1", listOf(video(qn = 80, width = 1920, height = 1080))).single()
        assertThat(stream.content).contains("80.m4s")
        assertThat(stream.codec).isEqualTo("avc1.640033")
        assertThat(stream.isVideoOnly).isTrue()
    }

    @Test
    fun `flac and dolby audio are dropped and the rest is kept`() {
        val streams =
            BilibiliStreamBridge.convertAudioFormats(
                "BV1",
                listOf(
                    audio(30251, "fLaC"),
                    audio(30250, "ec-3"),
                    audio(30280, "mp4a.40.2", bandwidth = 190_000),
                    audio(30216, "mp4a.40.5", bandwidth = 50_000),
                ),
            )
        assertThat(streams.map { it.averageBitrate }).containsExactly(190_000, 50_000).inOrder()
    }
}
