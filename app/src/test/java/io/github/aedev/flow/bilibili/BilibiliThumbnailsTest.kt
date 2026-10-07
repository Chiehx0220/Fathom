package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.ThumbnailQuality
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import org.junit.Test

class BilibiliThumbnailsTest {
    private val cover = "https://i1.hdslb.com/bfs/archive/b1dd7ea1003d08f84768b7c4da2a105f7551645b.jpg"

    @Test
    fun `high keeps the original cover`() {
        assertThat(bilibiliThumbnailCandidates(cover, ThumbnailQuality.HIGH, portrait = false)).containsExactly(cover)
    }

    @Test
    fun `medium and low ask the host for a narrower copy and fall back to the original`() {
        assertThat(bilibiliThumbnailCandidates(cover, ThumbnailQuality.MEDIUM, portrait = false))
            .containsExactly("$cover@672w.webp", cover)
            .inOrder()
        assertThat(bilibiliThumbnailCandidates(cover, ThumbnailQuality.LOW, portrait = false))
            .containsExactly("$cover@320w.webp", cover)
            .inOrder()
    }

    @Test
    fun `a portrait card never drops below 480 px`() {
        assertThat(bilibiliThumbnailCandidates(cover, ThumbnailQuality.LOW, portrait = true))
            .containsExactly("$cover@480w.webp", cover)
            .inOrder()
    }

    @Test
    fun `only an unresized Bilibili cover is resized`() {
        assertThat(bilibiliThumbnailCandidates("$cover@100w.webp", ThumbnailQuality.LOW, false)).isNull()
        assertThat(bilibiliThumbnailCandidates("https://i.ytimg.com/vi/abc/hq720.jpg", ThumbnailQuality.LOW, false)).isNull()
        assertThat(bilibiliThumbnailCandidates("https://example.com/bfs/a.jpg", ThumbnailQuality.LOW, false)).isNull()
        assertThat(bilibiliThumbnailCandidates("", ThumbnailQuality.LOW, false)).isNull()
    }

    @Test
    fun `the video thumbnail resolver sends a Bilibili cover through it`() {
        val low = ThumbnailUrlResolver.resolveVideoThumbnailCandidates("BV1bia36FEuo?p=1", cover, ThumbnailQuality.LOW)

        assertThat(low).containsExactly("$cover@320w.webp", cover).inOrder()
    }

    @Test
    fun `off fetches no Bilibili cover at all, but one stored on the device still shows`() {
        assertThat(bilibiliThumbnailCandidates(cover, ThumbnailQuality.OFF, portrait = false)).isNull()
        assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates("BV1bia36FEuo?p=1", cover, ThumbnailQuality.OFF)).isEmpty()

        val saved = "file:///storage/emulated/0/Download/Flow/cover.jpg"
        assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates("BV1bia36FEuo?p=1", saved, ThumbnailQuality.OFF))
            .containsExactly(saved)
    }

    @Test
    fun `a YouTube thumbnail is untouched`() {
        val low = ThumbnailUrlResolver.resolveVideoThumbnailCandidates("dQw4w9WgXcQ", null, ThumbnailQuality.LOW)

        val base = "https://i.ytimg.com/vi/dQw4w9WgXcQ"
        assertThat(low).containsExactly("$base/mqdefault.jpg", "$base/hqdefault.jpg").inOrder()
    }
}
