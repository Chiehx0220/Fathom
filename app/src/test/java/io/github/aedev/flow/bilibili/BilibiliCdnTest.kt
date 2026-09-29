package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BilibiliCdnTest {
    private val stable = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/a.m4s?e=1"
    private val backup = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/a.m4s?e=1"
    private val mcdn = "https://xy1x2x3x4.mcdn.bilivideo.cn:4483/upgcxcode/a.m4s?e=1"
    private val edge = "https://cn-tj.edge.mountaintoys.cn/upgcxcode/a.m4s?e=1"

    @Test
    fun `cdn hosts include the bilivideo cn and mountaintoys domains`() {
        listOf(stable, backup, mcdn, edge).forEach { assertThat(BilibiliCdn.isCdnUrl(it)).isTrue() }
        assertThat(BilibiliCdn.isCdnUrl("https://www.youtube.com/watch?v=x")).isFalse()
        assertThat(BilibiliCdn.isCdnUrl("not a url")).isFalse()
    }

    @Test
    fun `mcdn nodes are recognised by host or query`() {
        assertThat(BilibiliCdn.isMcdnUrl(mcdn)).isTrue()
        assertThat(BilibiliCdn.isMcdnUrl(edge)).isTrue()
        assertThat(BilibiliCdn.isMcdnUrl("$stable&os=mcdn")).isTrue()
        assertThat(BilibiliCdn.isMcdnUrl(stable)).isFalse()
    }

    @Test
    fun `stable urls drop mcdn nodes and keep the primary first`() {
        assertThat(BilibiliCdn.stableUrls(stable, listOf(mcdn, backup))).containsExactly(stable, backup).inOrder()
    }

    @Test
    fun `an mcdn primary gives way to the first stable backup`() {
        assertThat(BilibiliCdn.stableUrls(mcdn, listOf(edge, backup, stable))).containsExactly(backup, stable).inOrder()
    }

    @Test
    fun `the primary stays as a last resort when every candidate is mcdn`() {
        assertThat(BilibiliCdn.stableUrls(mcdn, listOf(edge))).containsExactly(mcdn)
    }
}
