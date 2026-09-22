package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Page

// Thin facade over HtmlRendererCommon - preserves every existing "HtmlRenderer.xxx(...)" call
// site. lightColors/darkColors stay here (not moved): ServerService sets them via
// "HtmlRenderer.lightColors = ...", WebTheme reads them back qualified for the SPA shell.
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
