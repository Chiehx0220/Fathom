package io.github.aedev.flow.player.datasource

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class BilibiliMirrorsTest {
    private val primary = "https://upos-sz-mirrorcosov.bilivideo.com/v/1.m4s?x=1"
    private val backup = "https://upos-hz-mirrorakam.akamaized.net/v/1.m4s?x=2"

    @After
    fun tearDown() = BilibiliMirrors.clearForTest()

    @Test
    fun `either mirror of a stream finds the same group`() {
        BilibiliMirrors.register(primary, listOf(backup))
        assertThat(BilibiliMirrors.groupFor(primary)).isSameInstanceAs(BilibiliMirrors.groupFor(backup))
        assertThat(BilibiliMirrors.groupFor(primary)).isNotNull()
    }

    @Test
    fun `the primary is tried first until a mirror wins`() {
        BilibiliMirrors.register(primary, listOf(backup))
        assertThat(BilibiliMirrors.groupFor(primary)!!.order()).containsExactly(primary, backup).inOrder()
    }

    @Test
    fun `the last winner is tried first next time`() {
        BilibiliMirrors.register(primary, listOf(backup))
        val group = BilibiliMirrors.groupFor(primary)!!
        group.markWinner(backup)
        assertThat(group.order()).containsExactly(backup, primary).inOrder()
    }

    private val bytes = 4L * 1024 * 1024

    @Test
    fun `the faster measured host is tried first`() {
        BilibiliMirrors.register(primary, listOf(backup))
        BilibiliMirrors.recordSpeed(primary, bytes, 40_000_000_000L, nowMs = 1_000)
        BilibiliMirrors.recordSpeed(backup, bytes, 4_000_000_000L, nowMs = 1_000)

        assertThat(BilibiliMirrors.groupFor(primary)!!.order(nowMs = 2_000)).containsExactly(backup, primary).inOrder()
    }

    @Test
    fun `a host with no measurement is tried before measured ones`() {
        BilibiliMirrors.register(primary, listOf(backup))
        BilibiliMirrors.recordSpeed(primary, bytes, 4_000_000_000L, nowMs = 1_000)

        assertThat(BilibiliMirrors.groupFor(primary)!!.order(nowMs = 2_000)).containsExactly(backup, primary).inOrder()
    }

    @Test
    fun `a measurement goes stale and the host is measured again`() {
        BilibiliMirrors.register(primary, listOf(backup))
        BilibiliMirrors.recordSpeed(primary, bytes, 40_000_000_000L, nowMs = 1_000)
        BilibiliMirrors.recordSpeed(backup, bytes, 4_000_000_000L, nowMs = 100_000)

        assertThat(BilibiliMirrors.speedOf(primary, nowMs = 100_000)).isNull()
        assertThat(BilibiliMirrors.groupFor(primary)!!.order(nowMs = 100_000)).containsExactly(primary, backup).inOrder()
    }

    @Test
    fun `short transfers do not count as a measurement`() {
        BilibiliMirrors.recordSpeed(primary, 10_000L, 1_000_000_000L, nowMs = 1_000)
        BilibiliMirrors.recordSpeed(primary, bytes, 10_000_000L, nowMs = 1_000)

        assertThat(BilibiliMirrors.speedOf(primary, nowMs = 1_500)).isNull()
    }

    @Test
    fun `the speed is per host and smoothed over transfers`() {
        BilibiliMirrors.recordSpeed(primary, bytes, 4_000_000_000L, nowMs = 1_000)
        BilibiliMirrors.recordSpeed("https://upos-sz-mirrorcosov.bilivideo.com/other/2.m4s", bytes, 2_000_000_000L, nowMs = 2_000)

        val speed = BilibiliMirrors.speedOf(primary, nowMs = 3_000)!!
        val first = bytes * 1e9 / 4_000_000_000L
        val second = bytes * 1e9 / 2_000_000_000L
        assertThat(speed).isWithin(1.0).of((first + second) / 2)
    }

    @Test
    fun `a stream with no backup has no group and is never hedged`() {
        BilibiliMirrors.register(primary, emptyList())
        assertThat(BilibiliMirrors.groupFor(primary)).isNull()
    }

    @Test
    fun `an unknown url has no group`() {
        assertThat(BilibiliMirrors.groupFor("https://example.com/x")).isNull()
    }

    @Test
    fun `duplicate urls are collapsed`() {
        BilibiliMirrors.register(primary, listOf(primary, backup))
        assertThat(BilibiliMirrors.groupFor(primary)!!.urls).containsExactly(primary, backup).inOrder()
    }
}
