package io.github.aedev.flow.localserver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerResumeTest {
    private class Memory : ResumeStore {
        override var armedAt = 0L
    }

    private var clock = 1_000_000L
    private val store = Memory()
    private val resume = ServerResume(store) { clock }

    @Test
    fun nothingArmedDoesNotResume() {
        assertFalse(resume.consume())
    }

    @Test
    fun aRecentMarkResumesOnce() {
        resume.arm()
        clock += 5_000
        assertTrue(resume.consume())
        assertFalse(resume.consume())
    }

    @Test
    fun aStaleMarkIsDroppedWithoutResuming() {
        resume.arm()
        clock += ServerResume.WINDOW_MS + 1
        assertFalse(resume.consume())
        assertTrue(store.armedAt == 0L)
    }

    @Test
    fun aDisarmedMarkDoesNotResume() {
        resume.arm()
        resume.disarm()
        assertFalse(resume.consume())
    }
}
