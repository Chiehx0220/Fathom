package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BilibiliLiveIdTest {
    @Test
    fun `a live id is told from a video id and a YouTube id`() {
        assertThat(BilibiliRef.Live.ofId("live:21452505") != null).isTrue()
        assertThat(BilibiliRef.Live.ofId("BV1xx411c7mD?p=1") != null).isFalse()
        assertThat(BilibiliRef.Live.ofId("dQw4w9WgXcQ") != null).isFalse()
        assertThat(BilibiliRef.Live.ofId("live:") != null).isFalse()
        assertThat(BilibiliRef.Live.ofId("live:abc") != null).isFalse()
    }

    @Test
    fun `both Bilibili id kinds count as Bilibili`() {
        assertThat(BilibiliRef.isVideoId("live:21452505")).isTrue()
        assertThat(BilibiliRef.isVideoId("BV1xx411c7mD?p=1")).isTrue()
        assertThat(BilibiliRef.isVideoId("dQw4w9WgXcQ")).isFalse()
        assertThat(serviceIdOfVideo("live:5", saved = 0)).isEqualTo(BILIBILI_SERVICE_ID)
    }

    @Test
    fun `the room number and link round trip through the id`() {
        assertThat(BilibiliRef.Live.ofId(BilibiliRef.Live(21452505).id)?.roomId).isEqualTo(21452505L)
        assertThat(BilibiliRef.Live.ofId("BV1xx411c7mD")?.roomId).isNull()
        assertThat(BilibiliRef.Live.ofId("live:21452505")?.url).isEqualTo("https://live.bilibili.com/21452505")
        assertThat(BilibiliRef.ofVideoId("live:21452505")?.url).isEqualTo("https://live.bilibili.com/21452505")
    }

    @Test
    fun `a live room link is read whatever the tracking parameters`() {
        assertThat(BilibiliRef.Live.ofUrl("https://live.bilibili.com/21452505?spm_id_from=333.1007")?.id).isEqualTo("live:21452505")
        assertThat(BilibiliRef.Live.ofUrl("https://live.bilibili.com/h5/21452505")?.id).isEqualTo("live:21452505")
        assertThat(BilibiliRef.Live.ofUrl("https://live.bilibili.com/")?.id).isNull()
        assertThat(BilibiliRef.Live.ofUrl("https://www.bilibili.com/video/BV1xx411c7mD")?.id).isNull()
    }

    @Test
    fun `a shared live link opens the room`() {
        val target = BilibiliDeepLink.parse("快来看直播 https://live.bilibili.com/21452505?share_source=copy")

        assertThat(target).isEqualTo(BilibiliRef.Live(21452505))
    }
}
