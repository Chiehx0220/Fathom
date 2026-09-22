package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BilibiliDeepLinkTest {
    @Test
    fun `a video link becomes a video id with its part`() {
        assertThat(BilibiliDeepLink.parse("https://www.bilibili.com/video/BV1SseM6oEvi"))
            .isEqualTo(BilibiliLinkTarget.Video("BV1SseM6oEvi?p=1"))
        assertThat(BilibiliDeepLink.parse("https://www.bilibili.com/video/BV1SseM6oEvi?p=3&t=20"))
            .isEqualTo(BilibiliLinkTarget.Video("BV1SseM6oEvi?p=3"))
        assertThat(BilibiliDeepLink.parse("https://m.bilibili.com/video/BV1SseM6oEvi/?share_source=copy_web&vd_source=abc"))
            .isEqualTo(BilibiliLinkTarget.Video("BV1SseM6oEvi?p=1"))
    }

    @Test
    fun `the first link of a shared text is used`() {
        assertThat(BilibiliDeepLink.parse("【a title】 https://www.bilibili.com/video/BV1SseM6oEvi/?p=2 来自哔哩哔哩"))
            .isEqualTo(BilibiliLinkTarget.Video("BV1SseM6oEvi?p=2"))
    }

    @Test
    fun `an uploader space link becomes an uploader`() {
        assertThat(BilibiliDeepLink.parse("https://space.bilibili.com/455557356?spm_id_from=333.999"))
            .isEqualTo(BilibiliLinkTarget.Uploader(455557356L))
        assertThat(BilibiliDeepLink.parse("https://m.bilibili.com/space/455557356"))
            .isEqualTo(BilibiliLinkTarget.Uploader(455557356L))
    }

    @Test
    fun `other hosts are not Bilibili links`() {
        assertThat(BilibiliDeepLink.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).isNull()
        assertThat(BilibiliDeepLink.parse("https://notbilibili.com/video/BV1SseM6oEvi")).isNull()
        assertThat(BilibiliDeepLink.parse("no link here")).isNull()
    }

    @Test
    fun `a b23 short link is recognised but needs a redirect to be read`() {
        assertThat(BilibiliDeepLink.isShortLink("【a title】 https://b23.tv/abc123")).isTrue()
        assertThat(BilibiliDeepLink.parse("https://b23.tv/abc123")).isNull()
        assertThat(BilibiliDeepLink.isShortLink("https://www.bilibili.com/video/BV1SseM6oEvi")).isFalse()
    }
}
