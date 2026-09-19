package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BilibiliPlaybackSourceTest {
    @Test
    fun `a plain bvid is the first part`() {
        assertThat(BilibiliPlaybackSource.parseVideoId("BV1V1em62ECa")).isEqualTo("BV1V1em62ECa" to 1)
    }

    @Test
    fun `the p parameter picks the part`() {
        assertThat(BilibiliPlaybackSource.parseVideoId("BV1jWej6hEnK?p=3")).isEqualTo("BV1jWej6hEnK" to 3)
    }

    @Test
    fun `p is found among other parameters`() {
        assertThat(BilibiliPlaybackSource.parseVideoId("BV1x?t=10&p=2")).isEqualTo("BV1x" to 2)
    }

    @Test
    fun `a missing, zero or unreadable p means the first part`() {
        assertThat(BilibiliPlaybackSource.parseVideoId("BV1x?p=0").second).isEqualTo(1)
        assertThat(BilibiliPlaybackSource.parseVideoId("BV1x?p=abc").second).isEqualTo(1)
        assertThat(BilibiliPlaybackSource.parseVideoId("BV1x?t=10").second).isEqualTo(1)
    }
}
