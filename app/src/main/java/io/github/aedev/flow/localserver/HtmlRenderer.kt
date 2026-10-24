package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page

// Facade over HtmlRendererCommon; lightColors/darkColors set by ServerService, read by WebTheme.
object HtmlRenderer {
    @JvmField
    var lightColors: MutableMap<String, String> = HashMap()

    @JvmField
    var darkColors: MutableMap<String, String> = HashMap()

    @JvmStatic
    fun deserializePage(b64: String?): Page? = HtmlRendererCommon.deserializePage(b64)

    @JvmStatic
    fun getThumbnailUrl(thumbnails: List<Image>?): String = HtmlRendererCommon.getThumbnailUrl(thumbnails)

    @JvmStatic
    fun getThumbnailUrl(url: String?): String = HtmlRendererCommon.getThumbnailUrl(url)
}
