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
