package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
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
        RemoteSession.getAndClearPendingCommands()
        RemoteSession.commandBroadcaster = null
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
    fun `a state with a video open or minimised has a video to control`() {
        assertThat(RemoteState().hasVideo).isFalse()
        assertThat(RemoteState(watching = true).hasVideo).isTrue()
        assertThat(RemoteState(minimized = true).hasVideo).isTrue()
    }

    @Test
    fun `commands wait in order until they are taken, once`() {
        RemoteSession.addPendingCommand("play_pause")
        RemoteSession.addPendingCommand("volume:1")

        assertThat(RemoteSession.getAndClearPendingCommands()).containsExactly("play_pause", "volume:1").inOrder()
        assertThat(RemoteSession.getAndClearPendingCommands()).isEmpty()
    }

    @Test
    fun `a command is also pushed to the connected pages`() {
        val pushed = mutableListOf<String>()
        RemoteSession.commandBroadcaster = { pushed += it }

        RemoteSession.addPendingCommand("mute")

        assertThat(pushed).containsExactly("mute")
    }

    @Test
    fun `a page that never collects its commands cannot grow the queue without bound`() {
        repeat(600) { RemoteSession.addPendingCommand("cmd$it") }

        val kept = RemoteSession.getAndClearPendingCommands()

        assertThat(kept).hasSize(501)
        assertThat(kept.first()).isEqualTo("cmd99")
        assertThat(kept.last()).isEqualTo("cmd599")
    }
}
