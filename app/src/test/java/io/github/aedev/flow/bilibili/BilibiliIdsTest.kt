package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BilibiliIdsTest {
    @Test
    fun midIsRecognisedFromNumberOrLink() {
        assertThat(BilibiliChannelId.midOf("455557356")).isEqualTo(455557356L)
        assertThat(BilibiliChannelId.midOf("https://space.bilibili.com/455557356")).isEqualTo(455557356L)
        assertThat(BilibiliChannelId.midOf("UCabc")).isNull()
        assertThat(BilibiliChannelId.midOf("@handle")).isNull()
        assertThat(BilibiliChannelId.midOf("")).isNull()
    }

    @Test
    fun playlistIdRoundTripsIncludingTheName() {
        val id = BilibiliPlaylistId.encode(BilibiliPlaylistKind.SERIES, 455557356L, 2244490L, "杰哥當爸爸: 第1季")
        val parsed = BilibiliPlaylistId.parse(id)
        assertThat(parsed).isEqualTo(BilibiliPlaylistId.Parsed(BilibiliPlaylistKind.SERIES, 455557356L, 2244490L, "杰哥當爸爸: 第1季"))
        assertThat(id.count { it == ':' }).isEqualTo(4)
    }

    @Test
    fun otherPlaylistIdsAreNotBilibiliOnes() {
        assertThat(BilibiliPlaylistId.parse("PLabc123")).isNull()
        assertThat(BilibiliPlaylistId.parse("bilibili:series:x:1:n")).isNull()
        assertThat(BilibiliPlaylistId.parse("bilibili:other:1:1:n")).isNull()
    }

    @Test
    fun bvidFallsBackToTheAvNumber() {
        assertThat(BilibiliSigning.bvidOf("BV1xx", 0)).isEqualTo("BV1xx")
        assertThat(BilibiliSigning.bvidOf("", 0)).isNull()
        assertThat(BilibiliSigning.bvidOf("", 170001)).isEqualTo(BilibiliSigning.av2bv(170001))
    }
}
