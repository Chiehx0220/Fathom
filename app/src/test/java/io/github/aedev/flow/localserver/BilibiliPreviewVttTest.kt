package io.github.aedev.flow.localserver

import io.github.aedev.flow.content.Frameset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliPreviewVttTest {
    private val sheet = "https://bimp.hdslb.com/videoshotpvhdboss/a-0001.jpg"
    private val frames = Frameset(listOf(sheet), 480, 270, 13, 5417, 10, 10)

    @Test
    fun `a frame wider than the tooltip is still the one used`() {
        assertSame(frames, listOf(frames).bestForSeekBar())
    }

    @Test
    fun `cues run from zero in even steps`() {
        val cues = frames.toWebVtt().split("\n\n").filter { "-->" in it }
        assertEquals(13, cues.size)
        assertTrue(cues[0].startsWith("00:00:00.000 --> 00:00:05.417"))
        assertTrue(cues[1].startsWith("00:00:05.417 --> 00:00:10.834"))
    }

    @Test
    fun `the eleventh frame starts the second row of the sheet`() {
        val cue = frames.toWebVtt().split("\n\n").filter { "-->" in it }[10]
        assertTrue(cue.endsWith("#xywh=0,270,480,270"))
    }

    @Test
    fun `sheet urls go through the image proxy`() {
        assertTrue("/image-proxy?u=" in frames.toWebVtt())
    }
}
