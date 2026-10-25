package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BilibiliIdsTest {
    private fun parts(raw: String) = BilibiliRef.Video.parse(raw).let { it.bvid to it.part }

    @Test
    fun midIsRecognisedFromNumberOrLink() {
        assertThat(BilibiliRef.Uploader.ofLink("455557356")?.mid).isEqualTo(455557356L)
        assertThat(BilibiliRef.Uploader.ofLink("https://space.bilibili.com/455557356")?.mid).isEqualTo(455557356L)
        assertThat(BilibiliRef.Uploader.ofLink("UCabc")?.mid).isNull()
        assertThat(BilibiliRef.Uploader.ofLink("@handle")?.mid).isNull()
        assertThat(BilibiliRef.Uploader.ofLink("")?.mid).isNull()
    }

    @Test
    fun playlistIdRoundTripsIncludingTheName() {
        val id = BilibiliRef.Series(BilibiliPlaylistKind.SERIES, 455557356L, 2244490L, "杰哥當爸爸: 第1季").id
        val parsed = BilibiliRef.Series.ofId(id)
        assertThat(parsed).isEqualTo(BilibiliRef.Series(BilibiliPlaylistKind.SERIES, 455557356L, 2244490L, "杰哥當爸爸: 第1季"))
        assertThat(id.count { it == ':' }).isEqualTo(4)
    }

    @Test
    fun otherPlaylistIdsAreNotBilibiliOnes() {
        assertThat(BilibiliRef.Series.ofId("PLabc123")).isNull()
        assertThat(BilibiliRef.Series.ofId("bilibili:series:x:1:n")).isNull()
        assertThat(BilibiliRef.Series.ofId("bilibili:other:1:1:n")).isNull()
    }

    @Test
    fun bvidFallsBackToTheAvNumber() {
        assertThat(BilibiliSigning.bvidOf("BV1xx", 0)).isEqualTo("BV1xx")
        assertThat(BilibiliSigning.bvidOf("", 0)).isNull()
        assertThat(BilibiliSigning.bvidOf("", 170001)).isEqualTo(BilibiliSigning.av2bv(170001))
    }

    @Test
    fun aPlainBvidIsTheFirstPart() {
        assertThat(parts("BV1V1em62ECa")).isEqualTo("BV1V1em62ECa" to 1)
    }

    @Test
    fun thePParameterPicksThePart() {
        assertThat(parts("BV1jWej6hEnK?p=3")).isEqualTo("BV1jWej6hEnK" to 3)
        assertThat(parts("BV1x?t=10&p=2")).isEqualTo("BV1x" to 2)
    }

    @Test
    fun aMissingZeroOrUnreadablePMeansTheFirstPart() {
        assertThat(parts("BV1x?p=0").second).isEqualTo(1)
        assertThat(parts("BV1x?p=abc").second).isEqualTo(1)
        assertThat(parts("BV1x?t=10").second).isEqualTo(1)
    }

    @Test
    fun aVideoLinkGivesItsIdBack() {
        assertThat(BilibiliRef.Video.ofUrl("https://www.bilibili.com/video/BV1SHM36KE6Y?p=2")?.id).isEqualTo("BV1SHM36KE6Y?p=2")
        assertThat(BilibiliRef.Video.ofUrl("https://www.bilibili.com/video/BV1SHM36KE6Y/")?.id).isEqualTo("BV1SHM36KE6Y")
        assertThat(BilibiliRef.Video.ofUrl("https://space.bilibili.com/455557356")?.id).isNull()
        assertThat(BilibiliRef.ofVideoId("BV1SHM36KE6Y?p=1")?.url).isEqualTo("https://www.bilibili.com/video/BV1SHM36KE6Y?p=1")
    }

    @Test
    fun shareTrackingParametersAreNotPartOfTheId() {
        assertThat(BilibiliRef.Video.ofUrl("https://www.bilibili.com/video/BV1SHM36KE6Y/?share_source=copy_web&vd_source=abc")?.id)
            .isEqualTo("BV1SHM36KE6Y")
        assertThat(BilibiliRef.Video.ofUrl("https://www.bilibili.com/video/BV1SHM36KE6Y?spm_id_from=1&p=3&t=20")?.id)
            .isEqualTo("BV1SHM36KE6Y?p=3")
        assertThat(BilibiliRef.Video.of("BV1SHM36KE6Y").id).isEqualTo("BV1SHM36KE6Y?p=1")
    }

    @Test
    fun aMobileSpaceLinkGivesTheUploaderId() {
        assertThat(BilibiliRef.Uploader.ofLink("https://m.bilibili.com/space/455557356")?.mid).isEqualTo(455557356L)
    }

    @Test
    fun onlyTheBvShapeCountsAsBilibili() {
        assertThat(BilibiliRef.isVideoId("BV1SHM36KE6Y?p=1")).isTrue()
        assertThat(BilibiliRef.isVideoId("BV1SHM36KE6Y")).isTrue()
        assertThat(BilibiliRef.isVideoId("dQw4w9WgXcQ")).isFalse()
    }

    @Test
    fun aLinkIsBilibiliByItsHostNotByItsText() {
        assertThat(BilibiliRef.isBilibiliUrl("https://space.bilibili.com/455557356")).isTrue()
        assertThat(BilibiliRef.isBilibiliUrl("https://www.bilibili.com/video/BV1x")).isTrue()
        assertThat(BilibiliRef.isBilibiliUrl("https://www.youtube.com/watch?v=bilibili.com")).isFalse()
        assertThat(BilibiliRef.isBilibiliUrl("https://notbilibili.com/x")).isFalse()
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
