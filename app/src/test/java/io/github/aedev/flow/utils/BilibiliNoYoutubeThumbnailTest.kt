package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BilibiliNoYoutubeThumbnailTest {
    @Test
    fun `a Bilibili video is never given a YouTube thumbnail link`() {
        assertThat(ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail("BV1xx411c7mD")).isEmpty()
        assertThat(ThumbnailUrlResolver.buildFallbackYoutubeThumbnail("BV1xx411c7mD?p=2")).isEmpty()
        assertThat(ThumbnailUrlResolver.youtubeThumbnailCandidates("live:12345")).isEmpty()
        assertThat(ThumbnailUrlResolver.normalizeVideoThumbnail("BV1xx411c7mD", null)).isEmpty()
        assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates("BV1xx411c7mD", null)).isEmpty()
    }

    @Test
    fun `a Bilibili cover is kept and a YouTube video still gets its thumbnail`() {
        val cover = "https://i0.hdslb.com/bfs/archive/c.jpg"

        assertThat(ThumbnailUrlResolver.normalizeVideoThumbnail("BV1xx411c7mD", cover)).isEqualTo(cover)
        assertThat(ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail("dQw4w9WgXcQ"))
            .isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg")
    }
}
