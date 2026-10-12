package io.github.aedev.flow.localserver

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.VideoStream

class DownloadQualitiesTest {
    private fun stream(
        id: String,
        resolution: String,
        codec: String,
    ) = VideoStream
        .Builder()
        .setId(id)
        .setContent("https://example.invalid/$id", true)
        .setMediaFormat(MediaFormat.MPEG_4)
        .setIsVideoOnly(true)
        .setResolution(resolution)
        .setItagItem(
            ItagItem(137, ItagItem.ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, resolution).also {
                it.codec = codec
                it.height = resolution.takeWhile(Char::isDigit).toInt()
            },
        ).build()

    @Test
    fun `heights are tallest first with their codecs in download preference order`() {
        val result =
            downloadQualities(
                listOf(
                    stream("a", "720p", "avc1.4d401f"),
                    stream("b", "1080p", "avc1.640028"),
                    stream("c", "1080p", "av01.0.08M.08"),
                    stream("d", "1080p", "vp09.00.40.08"),
                    stream("e", "1080p60", "avc1.64002a"),
                ),
            )
        assertEquals(listOf(1080, 720), result.map { it.height })
        assertEquals(listOf("vp9", "h264", "av1"), result[0].codecs)
        assertEquals(listOf("h264"), result[1].codecs)
    }

    @Test
    fun `no streams give no qualities`() {
        assertEquals(0, downloadQualities(emptyList()).size)
    }
}
