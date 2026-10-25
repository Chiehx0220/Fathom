package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.Image

// Shared page toolbox: formatting, escaping, thumbnails.
object WebFormat {
    /** Service display name: Bilibili fixed, others from registered extractor service. */
    @JvmStatic
    fun getServiceName(serviceId: Int): String {
        if (LocalServerBilibili.isBilibili(serviceId)) return "Bilibili"
        return try {
            val name =
                org.schabi.newpipe.extractor.NewPipe
                    .getService(serviceId)
                    .serviceInfo.name
            if (name.isNullOrEmpty()) "Fathom" else name
        } catch (e: Exception) {
            "Fathom"
        }
    }

    // Stock placeholder image of earlier builds; saved history/library entries still carry it: treated as no image.
    private const val LEGACY_PLACEHOLDER_ID = "photo-1611162617213-7d7a39e9b1d7"

    private fun isUsableImageUrl(url: String?): Boolean = !url.isNullOrBlank() && !url.contains(LEGACY_PLACEHOLDER_ID)

    private fun normalizeImageUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    }

    /** Largest usable image URL; "" → web client draws no image. */
    @JvmStatic
    fun getThumbnailUrl(thumbnails: List<Image>?): String {
        if (thumbnails != null && thumbnails.isNotEmpty()) {
            for (i in thumbnails.indices.reversed()) {
                val img = thumbnails[i]
                if (img != null && isUsableImageUrl(img.url)) {
                    return normalizeImageUrl(img.url)
                }
            }
        }
        return ""
    }

    @JvmStatic
    fun hasThumbnail(thumbnails: List<Image>?): Boolean = thumbnails != null && thumbnails.any { it != null && isUsableImageUrl(it.url) }

    // Image shape: items = URL string; StreamInfo/ChannelExtractor = List<Image>. This overload: URL.
    @JvmStatic
    fun getThumbnailUrl(url: String?): String = if (isUsableImageUrl(url)) normalizeImageUrl(url!!) else ""

    /** Presence check, singular-URL shape (as [hasThumbnail] list overload). */
    @JvmStatic
    fun hasThumbnail(url: String?): Boolean = isUsableImageUrl(url)

    @JvmStatic
    fun encodeUrl(url: String?): String =
        try {
            java.net.URLEncoder.encode(url, "UTF-8")
        } catch (e: Exception) {
            url ?: ""
        }

    /** JSON string literal; `<`, `>`, `&` escaped (safe inside <script>). */
    @JvmStatic
    fun jsonString(text: String?): String {
        val sb = StringBuilder("\"")
        for (c in text.orEmpty()) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '<' || c == '>' || c == '&' || c == ' ' || c == ' ' || c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
