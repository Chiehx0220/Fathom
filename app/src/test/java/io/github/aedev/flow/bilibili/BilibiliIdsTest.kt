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

    @Test
    fun aPlainBvidIsTheFirstPart() {
        assertThat(BilibiliVideoId.parse("BV1V1em62ECa")).isEqualTo("BV1V1em62ECa" to 1)
    }

    @Test
    fun thePParameterPicksThePart() {
        assertThat(BilibiliVideoId.parse("BV1jWej6hEnK?p=3")).isEqualTo("BV1jWej6hEnK" to 3)
        assertThat(BilibiliVideoId.parse("BV1x?t=10&p=2")).isEqualTo("BV1x" to 2)
    }

    @Test
    fun aMissingZeroOrUnreadablePMeansTheFirstPart() {
        assertThat(BilibiliVideoId.parse("BV1x?p=0").second).isEqualTo(1)
        assertThat(BilibiliVideoId.parse("BV1x?p=abc").second).isEqualTo(1)
        assertThat(BilibiliVideoId.parse("BV1x?t=10").second).isEqualTo(1)
    }

    @Test
    fun aVideoLinkGivesItsIdBack() {
        assertThat(BilibiliVideoId.fromUrl("https://www.bilibili.com/video/BV1SHM36KE6Y?p=2")).isEqualTo("BV1SHM36KE6Y?p=2")
        assertThat(BilibiliVideoId.fromUrl("https://www.bilibili.com/video/BV1SHM36KE6Y/")).isEqualTo("BV1SHM36KE6Y")
        assertThat(BilibiliVideoId.fromUrl("https://space.bilibili.com/455557356")).isNull()
        assertThat(BilibiliVideoId.toUrl("BV1SHM36KE6Y?p=1")).isEqualTo("https://www.bilibili.com/video/BV1SHM36KE6Y?p=1")
    }

    @Test
    fun onlyTheBvShapeCountsAsBilibili() {
        assertThat(BilibiliVideoId.isBilibili("BV1SHM36KE6Y?p=1")).isTrue()
        assertThat(BilibiliVideoId.isBilibili("BV1SHM36KE6Y")).isTrue()
        assertThat(BilibiliVideoId.isBilibili("dQw4w9WgXcQ")).isFalse()
    }

    @Test
    fun aLinkIsBilibiliByItsHostNotByItsText() {
        assertThat(BilibiliLink.isBilibili("https://space.bilibili.com/455557356")).isTrue()
        assertThat(BilibiliLink.isBilibili("https://www.bilibili.com/video/BV1x")).isTrue()
        assertThat(BilibiliLink.isBilibili("https://www.youtube.com/watch?v=bilibili.com")).isFalse()
        assertThat(BilibiliLink.isBilibili("https://notbilibili.com/x")).isFalse()
    }

    @Test
    fun aSavedServiceIdOfZeroIsCorrectedByTheVideoId() {
        assertThat(serviceIdOfVideo("BV1SHM36KE6Y?p=1", 0)).isEqualTo(BILIBILI_SERVICE_ID)
        assertThat(serviceIdOfVideo("dQw4w9WgXcQ", 0)).isEqualTo(0)
    }

    @Test
    fun aSavedServiceIdOfZeroIsCorrectedByTheChannelId() {
        assertThat(serviceIdOfChannel("455557356", 0)).isEqualTo(BILIBILI_SERVICE_ID)
        assertThat(serviceIdOfChannel(" 455557356 ", 0)).isEqualTo(BILIBILI_SERVICE_ID)
        assertThat(serviceIdOfChannel("UCabc123", 0)).isEqualTo(0)
        assertThat(serviceIdOfChannel("@handle", 0)).isEqualTo(0)
    }
}
