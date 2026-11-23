package io.github.aedev.flow.localserver

import io.github.aedev.flow.content.Frameset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewFlagTest {
    @Test
    fun `a video without storyboards has no preview`() {
        assertFalse(zooInfo().toDetail(0).hasPreview)
    }

    @Test
    fun `a video with a usable storyboard has one`() {
        val info = zooInfo().apply { previewFrames = listOf(Frameset(listOf("https://i.ytimg.com/sb/x.jpg"), 160, 90, 10, 2000, 5, 5)) }
        assertTrue(info.toDetail(0).hasPreview)
    }

    @Test
    fun `a storyboard that cannot be cut into frames is not a preview`() {
        val info = zooInfo().apply { previewFrames = listOf(Frameset(emptyList(), 160, 90, 10, 2000, 5, 5)) }
        assertFalse(info.toDetail(0).hasPreview)
    }
}
