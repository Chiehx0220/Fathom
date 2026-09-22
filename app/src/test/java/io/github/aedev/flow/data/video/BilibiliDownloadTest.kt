package io.github.aedev.flow.data.video

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BilibiliStreamFormat
import org.junit.Test

class BilibiliDownloadTest {
    private fun format(
        id: Int,
        codecs: String,
        height: Int = 0,
        bandwidth: Int = 1000,
    ) = BilibiliStreamFormat(
        id = id,
        url = "https://upos.example/$id.m4s",
        backupUrls = emptyList(),
        codecs = codecs,
        bandwidth = bandwidth,
        width = height * 16 / 9,
        height = height,
        frameRate = null,
        initRange = null,
        indexRange = null,
    )

    private val videos =
        listOf(
            format(80, "avc1.640032", 1080),
            format(81, "hev1.1.6.L120", 1080, bandwidth = 900),
            format(64, "avc1.640028", 720),
            format(32, "avc1.64001F", 480),
        )
    private val audios =
        listOf(
            format(30250, "ec-3", bandwidth = 500),
            format(30280, "mp4a.40.2", bandwidth = 192),
            format(30232, "mp4a.40.2", bandwidth = 132),
        )

    @Test
    fun `the best quality picks the tallest AVC track and the richest AAC track`() {
        val choice = BilibiliDownload.choose(videos, audios, targetHeight = 0)!!

        assertThat(choice.video.id).isEqualTo(80)
        assertThat(choice.audio!!.id).isEqualTo(30280)
    }

    @Test
    fun `a target height picks the tallest track that fits`() {
        assertThat(BilibiliDownload.choose(videos, audios, 720)!!.video.id).isEqualTo(64)
        assertThat(BilibiliDownload.choose(videos, audios, 600)!!.video.id).isEqualTo(32)
    }

    @Test
    fun `a target below every track falls back to the smallest`() {
        assertThat(BilibiliDownload.choose(videos, audios, 240)!!.video.id).isEqualTo(32)
    }

    @Test
    fun `other codecs are used only when there is no AVC track`() {
        val hevcOnly = listOf(format(81, "hev1.1.6.L120", 1080))

        assertThat(BilibiliDownload.choose(hevcOnly, audios, 0)!!.video.id).isEqualTo(81)
    }

    @Test
    fun `no video tracks means nothing to download`() {
        assertThat(BilibiliDownload.choose(emptyList(), audios, 0)).isNull()
    }

    @Test
    fun `a video without audio still downloads`() {
        assertThat(BilibiliDownload.choose(videos, emptyList(), 0)!!.audio).isNull()
    }
}
