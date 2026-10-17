package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import org.junit.Test

private const val YOUTUBE = 0

class BackupNonYouTubeTest {
    @Test
    fun `a Bilibili video link gives its id and page`() {
        assertThat(nonYouTubeVideoId("https://www.bilibili.com/video/BV1xx411c7mD?p=2", BILIBILI_SERVICE_ID)).isEqualTo("BV1xx411c7mD?p=2")
    }

    @Test
    fun `a link that is not a Bilibili video gives nothing`() {
        assertThat(nonYouTubeVideoId("https://example.com/watch", BILIBILI_SERVICE_ID)).isNull()
        assertThat(nonYouTubeVideoId("https://www.bilibili.com/video/BV1xx411c7mD", YOUTUBE)).isNull()
    }

    @Test
    fun `a Bilibili uploader id gives their space page`() {
        assertThat(nonYouTubeChannelUrlOrHome("12345", BILIBILI_SERVICE_ID)).isEqualTo("https://space.bilibili.com/12345")
    }

    @Test
    fun `an id the service does not know falls back to YouTube's home`() {
        assertThat(nonYouTubeChannelUrlOrHome("UCabc", BILIBILI_SERVICE_ID)).isEqualTo("https://www.youtube.com/")
    }

    @Test
    fun `a YouTube video's thumbnail follows from its id and Bilibili's is left blank`() {
        assertThat(fallbackThumbnail("abc123", YOUTUBE)).isEqualTo("https://i.ytimg.com/vi/abc123/hq720.jpg")
        assertThat(fallbackThumbnail("BV1xx411c7mD?p=1", BILIBILI_SERVICE_ID)).isEmpty()
    }

    @Test
    fun `an imported video id goes to upstream's parser for YouTube and to the link handler for Bilibili`() {
        val youtube: (String) -> String? = { "yt:$it" }
        assertThat(importedVideoId("https://youtu.be/x", YOUTUBE, youtube)).isEqualTo("yt:https://youtu.be/x")
        assertThat(importedVideoId("https://www.bilibili.com/video/BV1xx411c7mD", BILIBILI_SERVICE_ID, youtube))
            .isEqualTo("BV1xx411c7mD")
    }

    @Test
    fun `an imported uploader id is blank when a Bilibili link names none`() {
        val youtube: (String?) -> String = { "yt:$it" }
        assertThat(importedChannelId("https://youtube.com/channel/UC1", YOUTUBE, youtube)).isEqualTo("yt:https://youtube.com/channel/UC1")
        assertThat(importedChannelId(null, BILIBILI_SERVICE_ID, youtube)).isEmpty()
        assertThat(importedChannelId("https://space.bilibili.com/12345", BILIBILI_SERVICE_ID, youtube)).isEqualTo("12345")
    }

    @Test
    fun `a Takeout entry carries a YouTube channel link only for a UC id and a space link for Bilibili`() {
        val uc = "UC" + "a".repeat(22)
        val regex = Regex("UC[0-9A-Za-z_-]{22}")
        assertThat(takeoutChannelUrl(uc, YOUTUBE, regex)).isEqualTo("https://www.youtube.com/channel/$uc")
        assertThat(takeoutChannelUrl("@handle", YOUTUBE, regex)).isEqualTo("https://www.youtube.com/")
        assertThat(takeoutChannelUrl("12345", BILIBILI_SERVICE_ID, regex)).isEqualTo("https://space.bilibili.com/12345")
    }

    @Test
    fun `a Takeout entry links the watch page of its service`() {
        assertThat(takeoutVideoUrl("abc123", YOUTUBE)).isEqualTo("https://www.youtube.com/watch?v=abc123")
        assertThat(takeoutVideoUrl("BV1xx411c7mD", BILIBILI_SERVICE_ID)).isEqualTo("https://www.bilibili.com/video/BV1xx411c7mD")
    }
}
