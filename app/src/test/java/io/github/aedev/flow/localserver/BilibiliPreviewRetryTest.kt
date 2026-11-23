package io.github.aedev.flow.localserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BilibiliPreviewRetryTest {
    private val created = 1_000_000L
    private val minute = 60_000L

    private fun due(
        hasFrames: Boolean = false,
        now: Long,
        nextTry: Long = created + 30_000L,
    ) = LocalServerBilibiliStreams.previewRetryDue(hasFrames, now, nextTry, created)

    @Test
    fun `missing previews are retried once the spacing has passed`() {
        assertTrue(due(now = created + 31_000L))
    }

    @Test
    fun `a retry is not due before the spacing has passed`() {
        assertFalse(due(now = created + 10_000L))
    }

    @Test
    fun `previews that arrived are never retried`() {
        assertFalse(due(hasFrames = true, now = created + 5 * minute))
    }

    @Test
    fun `retrying stops after ten minutes`() {
        assertTrue(due(now = created + 10 * minute, nextTry = created))
        assertFalse(due(now = created + 10 * minute + 1, nextTry = created))
    }
}
