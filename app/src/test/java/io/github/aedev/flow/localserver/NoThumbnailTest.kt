package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.schabi.newpipe.extractor.Image

class NoThumbnailTest {
    private val legacyPlaceholder =
        "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?q=80&w=300&auto=format&fit=crop"

    private fun image(url: String) = Image(url, -1, -1, Image.ResolutionLevel.UNKNOWN)

    @Test
    fun `a missing thumbnail is an empty url, not a stock image`() {
        assertThat(WebFormat.getThumbnailUrl(null as String?)).isEqualTo("")
        assertThat(WebFormat.getThumbnailUrl("  ")).isEqualTo("")
        assertThat(WebFormat.getThumbnailUrl(null as List<Image>?)).isEqualTo("")
        assertThat(WebFormat.getThumbnailUrl(emptyList<Image>())).isEqualTo("")
        assertThat(WebFormat.hasThumbnail(null as String?)).isFalse()
        assertThat(WebFormat.hasThumbnail(emptyList<Image>())).isFalse()
    }

    @Test
    fun `the placeholder saved by earlier builds also reads as no image`() {
        assertThat(WebFormat.getThumbnailUrl(legacyPlaceholder)).isEqualTo("")
        assertThat(WebFormat.hasThumbnail(legacyPlaceholder)).isFalse()
        assertThat(WebFormat.getThumbnailUrl(listOf(image(legacyPlaceholder)))).isEqualTo("")
    }

    @Test
    fun `a real thumbnail is kept, protocol-relative ones upgraded to https`() {
        assertThat(WebFormat.getThumbnailUrl("https://i.ytimg.com/vi/x/hq.jpg")).isEqualTo("https://i.ytimg.com/vi/x/hq.jpg")
        assertThat(WebFormat.getThumbnailUrl("//i0.hdslb.com/a.jpg")).isEqualTo("https://i0.hdslb.com/a.jpg")
        assertThat(WebFormat.hasThumbnail(listOf(image("https://i.ytimg.com/vi/x/hq.jpg")))).isTrue()
    }
}
