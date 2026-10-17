package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PeerLockoutTest {
    private var time = 1_000L
    private val lockout = PeerLockout(maxMisses = 3, lockMs = 10_000L, maxPeers = 2) { time }

    @Test
    fun `a peer is free until it misses the limit`() {
        repeat(2) { lockout.recordMiss("a") }

        assertThat(lockout.remainingMs("a")).isEqualTo(0)
    }

    @Test
    fun `the last allowed miss locks the peer for the whole period`() {
        repeat(3) { lockout.recordMiss("a") }

        assertThat(lockout.remainingMs("a")).isEqualTo(10_000L)
        time += 4_000L
        assertThat(lockout.remainingMs("a")).isEqualTo(6_000L)
    }

    @Test
    fun `a lock ends and the count starts over`() {
        repeat(3) { lockout.recordMiss("a") }
        time += 10_001L

        assertThat(lockout.remainingMs("a")).isEqualTo(0)
        repeat(2) { lockout.recordMiss("a") }
        assertThat(lockout.remainingMs("a")).isEqualTo(0)
    }

    @Test
    fun `peers are counted apart`() {
        repeat(3) { lockout.recordMiss("a") }

        assertThat(lockout.remainingMs("b")).isEqualTo(0)
    }

    @Test
    fun `clearing a peer or everyone lifts the lock`() {
        repeat(3) { lockout.recordMiss("a") }
        repeat(3) { lockout.recordMiss("b") }

        lockout.clear("a")
        assertThat(lockout.remainingMs("a")).isEqualTo(0)
        assertThat(lockout.remainingMs("b")).isGreaterThan(0)

        lockout.clear()
        assertThat(lockout.remainingMs("b")).isEqualTo(0)
    }

    @Test
    fun `expired records are dropped once too many peers are tracked`() {
        repeat(3) { lockout.recordMiss("a") }
        time += 10_001L
        lockout.recordMiss("b")
        lockout.recordMiss("c")
        lockout.recordMiss("d")

        assertThat(lockout.remainingMs("a")).isEqualTo(0)
    }
}
