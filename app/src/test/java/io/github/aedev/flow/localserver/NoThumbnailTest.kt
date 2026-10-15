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
        assertThat(HtmlRendererCommon.getThumbnailUrl(null as String?)).isEqualTo("")
        assertThat(HtmlRendererCommon.getThumbnailUrl("  ")).isEqualTo("")
        assertThat(HtmlRendererCommon.getThumbnailUrl(null as List<Image>?)).isEqualTo("")
        assertThat(HtmlRendererCommon.getThumbnailUrl(emptyList<Image>())).isEqualTo("")
        assertThat(HtmlRendererCommon.hasThumbnail(null as String?)).isFalse()
        assertThat(HtmlRendererCommon.hasThumbnail(emptyList<Image>())).isFalse()
    }

    @Test
    fun `the placeholder saved by earlier builds also reads as no image`() {
        assertThat(HtmlRendererCommon.getThumbnailUrl(legacyPlaceholder)).isEqualTo("")
        assertThat(HtmlRendererCommon.hasThumbnail(legacyPlaceholder)).isFalse()
        assertThat(HtmlRendererCommon.getThumbnailUrl(listOf(image(legacyPlaceholder)))).isEqualTo("")
    }

    @Test
    fun `a real thumbnail is kept, protocol-relative ones upgraded to https`() {
        assertThat(HtmlRendererCommon.getThumbnailUrl("https://i.ytimg.com/vi/x/hq.jpg")).isEqualTo("https://i.ytimg.com/vi/x/hq.jpg")
        assertThat(HtmlRendererCommon.getThumbnailUrl("//i0.hdslb.com/a.jpg")).isEqualTo("https://i0.hdslb.com/a.jpg")
        assertThat(HtmlRendererCommon.hasThumbnail(listOf(image("https://i.ytimg.com/vi/x/hq.jpg")))).isTrue()
    }
}
