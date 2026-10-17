package io.github.aedev.flow.localserver

import android.util.Base64
import io.github.aedev.flow.bilibili.BilibiliLink
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.localization.DateWrapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale

// Shared page toolbox: formatting, escaping, thumbnails, page (de)serialization.
object HtmlRendererCommon {
    // Fallback avatar palette by name hash (avatarColorFor()).
    @JvmField
    val AVATAR_COLORS =
        arrayOf(
            "#ff5722",
            "#e91e63",
            "#9c27b0",
            "#673ab7",
            "#3f51b5",
            "#2196f3",
            "#03a9f4",
            "#00bcd4",
            "#009688",
            "#4caf50",
            "#8bc34a",
            "#cddc39",
            "#ffc107",
            "#ff9800",
        )

    // Leading "@" trimmed (comment authors are handles).
    @JvmStatic
    fun avatarInitial(name: String?): String {
        val trimmed = name?.trimStart('@')
        return if (!trimmed.isNullOrEmpty()) trimmed.substring(0, 1).uppercase() else "?"
    }

    @JvmStatic
    fun avatarColorFor(name: String): String = AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.size]

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

    @JvmStatic
    fun serializePage(page: Page?): String? {
        if (page == null) return null
        return try {
            val baos = ByteArrayOutputStream()
            val oos = ObjectOutputStream(baos)
            oos.writeObject(page)
            oos.close()
            Base64.encodeToString(baos.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // serializePage() + escapeJs() for load-more onclick; null if no next page or serialization failed.
    @JvmStatic
    fun serializePageJs(page: Page?): String? {
        val serialized = serializePage(page) ?: return null
        return escapeJs(serialized)
    }

    @JvmStatic
    fun deserializePage(b64: String?): Page? {
        if (b64.isNullOrEmpty()) return null
        return try {
            val bytes = Base64.decode(b64, Base64.URL_SAFE)
            val bais = ByteArrayInputStream(bytes)
            val ois = ObjectInputStream(bais)
            val page = ois.readObject() as Page
            ois.close()
            page
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Subscriptions, bookmarked playlists, watch-later mix services; page-level serviceId = active tab only
     * (Bilibili channel → "/channel?serviceId=0&id=..." rejected by YouTube). Item service derived from own URL, page's as fallback.
     */
    @JvmStatic
    fun serviceOf(
        fallback: Int,
        url: String,
    ): Int {
        if (BilibiliLink.isBilibili(url)) return LocalServerBilibili.serviceId
        return try {
            org.schabi.newpipe.extractor.NewPipe
                .getServiceByUrl(url)
                .serviceId
        } catch (e: Exception) {
            fallback
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

    // YoutubeService forces hl=zu: textual upload date is Zulu; DateWrapper reliable → date formatted here.
    @JvmStatic
    fun formatUploadDate(
        uploadDate: DateWrapper?,
        textualFallback: String?,
    ): String {
        if (uploadDate != null) {
            return uploadDate.offsetDateTime().toLocalDate().toString()
        }
        return textualFallback ?: ""
    }

    @JvmStatic
    fun formatCount(count: Long): String {
        if (count < 0) return ""
        return if (count < 1000) {
            count.toString()
        } else if (count < 1000000) {
            val v = count / 1000.0
            if (v >= 100) {
                String.format(Locale.US, "%.0fK", v)
            } else {
                String.format(Locale.US, "%.1fK", v).replace(".0K", "K")
            }
        } else if (count < 1000000000) {
            val v = count / 1000000.0
            if (v >= 100) {
                String.format(Locale.US, "%.0fM", v)
            } else {
                String.format(Locale.US, "%.1fM", v).replace(".0M", "M")
            }
        } else {
            val v = count / 1000000000.0
            String.format(Locale.US, "%.1fB", v).replace(".0B", "B")
        }
    }

    @JvmStatic
    fun encodeUrl(url: String?): String =
        try {
            java.net.URLEncoder.encode(url, "UTF-8")
        } catch (e: Exception) {
            url ?: ""
        }

    // Also escapes apostrophe (single-quoted JS literals).
    @JvmStatic
    fun escapeJs(str: String?): String {
        if (str == null) return ""
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    // Raw titles/names as HTML text; escapes '"' for attributes. Not for getDescription() (already HTML).
    @JvmStatic
    fun escapeHtml(str: String?): String {
        if (str == null) return ""
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
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
