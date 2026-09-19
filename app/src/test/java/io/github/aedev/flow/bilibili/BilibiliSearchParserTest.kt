package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BilibiliSearchParserTest {
    @Test
    fun cleansKeywordMarkupAndEntities() {
        assertThat(BilibiliSearchParser.cleanTitle("<em class=\"keyword\">音樂</em> &amp; &#39;live&#39; &quot;x&quot;"))
            .isEqualTo("音樂 & 'live' \"x\"")
    }

    @Test
    fun parsesDurations() {
        assertThat(BilibiliSearchParser.parseDuration("3:25")).isEqualTo(205)
        assertThat(BilibiliSearchParser.parseDuration("1:02:03")).isEqualTo(3723)
        assertThat(BilibiliSearchParser.parseDuration("")).isEqualTo(0)
        assertThat(BilibiliSearchParser.parseDuration("x:1")).isEqualTo(0)
    }

    @Test
    fun mapsVideoAndUserRows() {
        val video =
            BilibiliSearchParser.toItem(
                SearchResponse.Item(
                    type = "video", bvid = "BV1x", title = "t", pic = "//i0.hdslb.com/a.jpg",
                    duration = "1:00", play = 5, author = "a", mid = 7, upic = "//i0.hdslb.com/u.jpg", pubdate = 9,
                ),
            ) as BilibiliSearchItem.Video
        assertThat(video.thumbnailUrl).isEqualTo("https://i0.hdslb.com/a.jpg")
        assertThat(video.durationSec).isEqualTo(60)
        assertThat(video.uploader.avatarUrl).isEqualTo("https://i0.hdslb.com/u.jpg")
        val user = BilibiliSearchParser.toItem(SearchResponse.Item(type = "bili_user", mid = 3, uname = "n", fans = 10))
        assertThat(user).isInstanceOf(BilibiliSearchItem.User::class.java)
        assertThat(BilibiliSearchParser.toItem(SearchResponse.Item(type = "live_room"))).isNull()
    }
}
