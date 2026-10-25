package io.github.aedev.flow.localserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadChoiceTest {
    @Test
    fun `no parameters follow the default quality`() {
        val choice = downloadChoice(Query(emptyMap()))
        assertNull(choice.height)
        assertFalse(choice.audioOnly)
    }

    @Test
    fun `a height is taken, 0 meaning the best`() {
        assertEquals(1080, downloadChoice(Query(mapOf("height" to "1080"))).height)
        assertEquals(0, downloadChoice(Query(mapOf("height" to "0"))).height)
    }

    @Test
    fun `a height that is not a sane number is ignored`() {
        assertNull(downloadChoice(Query(mapOf("height" to "-1"))).height)
        assertNull(downloadChoice(Query(mapOf("height" to "99999"))).height)
        assertNull(downloadChoice(Query(mapOf("height" to "tall"))).height)
    }

    @Test
    fun `a codec is taken only from the known keys`() {
        assertEquals("vp9", downloadChoice(Query(mapOf("codec" to "VP9"))).codec)
        assertNull(downloadChoice(Query(mapOf("codec" to "mpeg2"))).codec)
        assertNull(downloadChoice(Query(emptyMap())).codec)
    }

    @Test
    fun `audio only is asked with audio=1`() {
        assertTrue(downloadChoice(Query(mapOf("audio" to "1"))).audioOnly)
        assertFalse(downloadChoice(Query(mapOf("audio" to "true"))).audioOnly)
    }
}
