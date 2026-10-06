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
}
