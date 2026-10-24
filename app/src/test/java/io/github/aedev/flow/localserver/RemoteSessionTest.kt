package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class RemoteSessionTest {
    @Before
    fun clean() = reset()

    @After
    fun cleanUp() = reset()

    private fun reset() {
        RemoteSession.releaseLock()
        RemoteSession.commandBroadcaster = null
        RemoteSession.listenerCount = { 0 }
        RemoteSession.listenerCloser = null
        RemoteSession.takeHeldCommands()
    }

    @Test
    fun `the first page to ask holds the lock and its details are published`() {
        assertThat(RemoteSession.tryLock("code-a", "10.0.0.5", "A video")).isTrue()

        assertThat(RemoteSession.getActiveLockCode()).isEqualTo("code-a")
        assertThat(RemoteSession.getActiveClientIp()).isEqualTo("10.0.0.5")
        assertThat(RemoteSession.remoteLock.value).isEqualTo(RemoteLock(true, "10.0.0.5", "A video"))
    }

    @Test
    fun `the same code keeps the lock and only updates the title`() {
        RemoteSession.tryLock("code-a", "10.0.0.5", "First")

        assertThat(RemoteSession.tryLock("code-a", "10.0.0.9", "Second")).isTrue()

        assertThat(RemoteSession.getActiveVideoTitle()).isEqualTo("Second")
        assertThat(RemoteSession.getActiveClientIp()).isEqualTo("10.0.0.5")
    }

    @Test
    fun `another page's code is refused while the lock is held`() {
        RemoteSession.tryLock("code-a", "10.0.0.5", "First")

        assertThat(RemoteSession.tryLock("code-b", "10.0.0.7", "Other")).isFalse()

        assertThat(RemoteSession.getActiveLockCode()).isEqualTo("code-a")
        assertThat(RemoteSession.getActiveVideoTitle()).isEqualTo("First")
    }

    @Test
    fun `releasing clears the lock and what the page last reported`() {
        RemoteSession.tryLock("code-a", "10.0.0.5", "First")
        RemoteSession.updateRemoteState(RemoteState(watching = true, title = "First", paused = false))

        RemoteSession.releaseLock()

        assertThat(RemoteSession.getActiveLockCode()).isNull()
        assertThat(RemoteSession.remoteLock.value.locked).isFalse()
        assertThat(RemoteSession.remoteState.value).isEqualTo(RemoteState())
        assertThat(RemoteSession.tryLock("code-b", "10.0.0.7", "Other")).isTrue()
    }

    @Test
    fun `a takeover hands the lock over, closes the old sockets and drops the old state but keeps held commands`() {
        var closed = 0
        RemoteSession.listenerCloser = { closed++ }
        RemoteSession.tryLock("old", "10.0.0.5", "Old")
        RemoteSession.updateRemoteState(RemoteState(watching = true, title = "Old"))
        RemoteSession.sendOrHold("held")

        assertThat(RemoteSession.takeOver("new", "10.0.0.9", "New")).isTrue()

        assertThat(RemoteSession.getActiveLockCode()).isEqualTo("new")
        assertThat(RemoteSession.getActiveClientIp()).isEqualTo("10.0.0.9")
        assertThat(RemoteSession.remoteState.value.hasVideo).isFalse()
        assertThat(closed).isEqualTo(1)
        assertThat(RemoteSession.takeHeldCommands()).containsExactly("held")
        assertThat(RemoteSession.tryLock("old", "10.0.0.5", "Old")).isFalse()
    }

    @Test
    fun `a state with a video open or minimised has a video to control`() {
        assertThat(RemoteState().hasVideo).isFalse()
        assertThat(RemoteState(watching = true).hasVideo).isTrue()
        assertThat(RemoteState(minimized = true).hasVideo).isTrue()
    }

    @Test
    fun `a page that stopped reporting without releasing is no longer alive`() {
        RemoteSession.tryLock("code-a", "10.0.0.5", null)
        RemoteSession.markSeen(now = 1_000)

        assertThat(RemoteSession.isPageAlive(now = 1_000 + RemoteSession.PAGE_ALIVE_MS)).isTrue()
        assertThat(RemoteSession.isPageAlive(now = 1_001 + RemoteSession.PAGE_ALIVE_MS)).isFalse()
        assertThat(RemoteSession.remoteLock.value.locked).isFalse()
        assertThat(RemoteSession.tryLock("code-b", "10.0.0.6", null)).isTrue()
    }

    @Test
    fun `a page with an open socket stays alive however long since its last report`() {
        RemoteSession.tryLock("code-a", "10.0.0.5", null)
        RemoteSession.markSeen(now = 0)
        RemoteSession.listenerCount = { 1 }

        assertThat(RemoteSession.isPageAlive(now = 10 * RemoteSession.PAGE_ALIVE_MS)).isTrue()
    }

    @Test
    fun `a command is broadcast when a socket is open`() {
        val pushed = mutableListOf<String>()
        RemoteSession.commandBroadcaster = { pushed += it }
        RemoteSession.listenerCount = { 1 }

        assertThat(RemoteSession.sendOrHold("play_video:x")).isTrue()

        assertThat(pushed).containsExactly("play_video:x")
        assertThat(RemoteSession.takeHeldCommands()).isEmpty()
    }

    @Test
    fun `a command with no socket is held for the next page until it expires`() {
        assertThat(RemoteSession.sendOrHold("old", now = 0)).isFalse()
        assertThat(RemoteSession.sendOrHold("fresh", now = RemoteSession.HELD_TTL_MS)).isFalse()

        assertThat(RemoteSession.takeHeldCommands(now = RemoteSession.HELD_TTL_MS + 1)).containsExactly("fresh")
        assertThat(RemoteSession.takeHeldCommands()).isEmpty()
    }

    @Test
    fun `no page is alive without a lock`() {
        assertThat(RemoteSession.isPageAlive()).isFalse()
    }

    @Test
    fun `a command is pushed to the connected pages`() {
        val pushed = mutableListOf<String>()
        RemoteSession.commandBroadcaster = { pushed += it }

        RemoteSession.broadcast("mute")

        assertThat(pushed).containsExactly("mute")
    }

    @Test
    fun `a handed off video reaches the collector that is listening`() =
        runTest(UnconfinedTestDispatcher()) {
            val received = mutableListOf<Handoff>()
            val job = launch { RemoteSession.handoffs.collect { received += it } }
            val handoff = Handoff("https://www.youtube.com/watch?v=abc", "A video", "abc", 61_500L)

            assertThat(RemoteSession.handOff(handoff)).isTrue()

            assertThat(received).containsExactly(handoff)
            job.cancel()
        }
}
