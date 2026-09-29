package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BilibiliLiveIdTest {
    @Test
    fun `a live id is told from a video id and a YouTube id`() {
        assertThat(BilibiliLiveId.isLive("live:21452505")).isTrue()
        assertThat(BilibiliLiveId.isLive("BV1xx411c7mD?p=1")).isFalse()
        assertThat(BilibiliLiveId.isLive("dQw4w9WgXcQ")).isFalse()
        assertThat(BilibiliLiveId.isLive("live:")).isFalse()
        assertThat(BilibiliLiveId.isLive("live:abc")).isFalse()
    }

    @Test
    fun `both Bilibili id kinds count as Bilibili`() {
        assertThat(BilibiliVideoId.isBilibili("live:21452505")).isTrue()
        assertThat(BilibiliVideoId.isBilibili("BV1xx411c7mD?p=1")).isTrue()
        assertThat(BilibiliVideoId.isBilibili("dQw4w9WgXcQ")).isFalse()
        assertThat(serviceIdOfVideo("live:5", saved = 0)).isEqualTo(BILIBILI_SERVICE_ID)
    }

    @Test
    fun `the room number and link round trip through the id`() {
        assertThat(BilibiliLiveId.roomIdOf(BilibiliLiveId.of(21452505))).isEqualTo(21452505L)
        assertThat(BilibiliLiveId.roomIdOf("BV1xx411c7mD")).isNull()
        assertThat(BilibiliLiveId.toUrl("live:21452505")).isEqualTo("https://live.bilibili.com/21452505")
        assertThat(BilibiliVideoId.toUrl("live:21452505")).isEqualTo("https://live.bilibili.com/21452505")
    }

    @Test
    fun `a live room link is read whatever the tracking parameters`() {
        assertThat(BilibiliLiveId.fromUrl("https://live.bilibili.com/21452505?spm_id_from=333.1007")).isEqualTo("live:21452505")
        assertThat(BilibiliLiveId.fromUrl("https://live.bilibili.com/h5/21452505")).isEqualTo("live:21452505")
        assertThat(BilibiliLiveId.fromUrl("https://live.bilibili.com/")).isNull()
        assertThat(BilibiliLiveId.fromUrl("https://www.bilibili.com/video/BV1xx411c7mD")).isNull()
    }

    @Test
    fun `a shared live link opens the room`() {
        val target = BilibiliDeepLink.parse("快来看直播 https://live.bilibili.com/21452505?share_source=copy")

        assertThat(target).isEqualTo(BilibiliLinkTarget.Video("live:21452505"))
    }
}
