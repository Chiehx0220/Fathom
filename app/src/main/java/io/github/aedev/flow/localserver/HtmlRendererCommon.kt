package io.github.aedev.flow.localserver

import android.util.Base64

import io.github.aedev.flow.bilibili.BilibiliLink
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale

// Shared page toolbox: formatting, escaping, thumbnails, and page (de)serialization. The markup lives in WebUi and WebShell.
object HtmlRendererCommon {

    // Fallback avatar palette, picked by name hash (avatarColorFor()).
    @JvmField
    val AVATAR_COLORS = arrayOf("#ff5722", "#e91e63", "#9c27b0", "#673ab7", "#3f51b5", "#2196f3", "#03a9f4", "#00bcd4", "#009688", "#4caf50", "#8bc34a", "#cddc39", "#ffc107", "#ff9800")

    // Leading "@" is trimmed: comment authors are often handles.
    @JvmStatic
    fun avatarInitial(name: String?): String {
        val trimmed = name?.trimStart('@')
        return if (!trimmed.isNullOrEmpty()) trimmed.substring(0, 1).uppercase() else "?"
    }

    @JvmStatic
    fun avatarColorFor(name: String): String = AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.size]

    // Cache-busting tag derived from the content of the files under assets/web.
    val STATIC_ASSET_VERSION: String get() = WebAssets.version

    /** Service display name: Bilibili's is fixed, everything else comes from the registered extractor service. */
    @JvmStatic
    fun getServiceName(serviceId: Int): String {
        if (LocalServerBilibili.isBilibili(serviceId)) return "Bilibili"
        return try {
            val name = org.schabi.newpipe.extractor.NewPipe.getService(serviceId).serviceInfo.name
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

    // serializePage() + escapeJs() in one step for a load-more onclick. Null when there is no next page or serialization failed.
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

    // ---- Kept for existing call sites; the markup itself is built by WebShell and WebUi. ----

    @JvmStatic
    @JvmOverloads
    fun getHeaderHtml(activeServiceId: Int, query: String?, activeTab: String = "youtube"): String = WebShell.header(activeServiceId, query, activeTab)

    @JvmStatic
    @JvmOverloads
    fun wrapInTemplate(title: String?, bodyContent: String, isTv: Boolean, needsPlayer: Boolean = false): String =
        WebShell.page(title, bodyContent, isTv, needsPlayer)

    /**
     * Subscriptions, bookmarked playlists and watch-later can mix items from several services, but the page-level serviceId only
     * reflects the active tab. Linking every item with it sent a Bilibili channel to "/channel?serviceId=0&id=...", which the
     * YouTube service rejects. So each item's real service is worked out from its own URL, falling back to the page's.
     */
    @JvmStatic
    fun serviceOf(fallback: Int, url: String): Int {
        if (BilibiliLink.isBilibili(url)) return LocalServerBilibili.serviceId
        return try {
            org.schabi.newpipe.extractor.NewPipe.getServiceByUrl(url).serviceId
        } catch (e: Exception) {
            fallback
        }
    }

    @JvmStatic
    fun renderGrid(sb: StringBuilder, serviceId: Int, items: List<InfoItem>, showDeleteButton: Boolean = false, fallbackAvatarUrl: String? = null) {
        sb.append(WebUi.grid(serviceId, items, showDeleteButton, fallbackAvatarUrl))
    }

    @JvmStatic
    fun renderSubscribeButton(uploaderUrl: String, uploaderName: String, uploaderAvatarUrl: String, backUrlEncoded: String, isSubscribed: Boolean): String =
        WebUi.subscribeButton(uploaderUrl, uploaderName, uploaderAvatarUrl, backUrlEncoded, isSubscribed)

    @JvmStatic
    fun renderWatchLaterButton(info: StreamInfo, serviceId: Int, isWatchLater: Boolean): String = WebUi.watchLaterButton(info, serviceId, isWatchLater)

    @JvmStatic
    @JvmOverloads
    fun renderLikeDislikePill(info: StreamInfo, likeState: String?, includeDislike: Boolean = true): String = WebUi.likeDislike(info, likeState, includeDislike)

    // getThumbnailUrl() overloads always return a non-empty fallback: check the raw field for null/empty to test for an image.
    private const val NO_THUMBNAIL_PLACEHOLDER =
        "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?q=80&w=300&auto=format&fit=crop"

    private fun normalizeImageUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    }

    @JvmStatic
    fun getThumbnailUrl(thumbnails: List<Image>?): String {
        if (thumbnails != null && thumbnails.isNotEmpty()) {
            for (i in thumbnails.indices.reversed()) {
                val img = thumbnails[i]
                if (img != null && !img.url.isNullOrBlank()) {
                    return normalizeImageUrl(img.url)
                }
            }
        }
        return NO_THUMBNAIL_PLACEHOLDER
    }

    /** Raw presence check, unlike [getThumbnailUrl]'s placeholder-substituted output. */
    @JvmStatic
    fun hasThumbnail(thumbnails: List<Image>?): Boolean =
        thumbnails != null && thumbnails.any { it != null && !it.url.isNullOrBlank() }

    // Image shape differs by source: items carry a URL string, StreamInfo/ChannelExtractor a List<Image>. This overload takes the URL.
    @JvmStatic
    fun getThumbnailUrl(url: String?): String {
        if (!url.isNullOrBlank()) {
            return normalizeImageUrl(url)
        }
        return NO_THUMBNAIL_PLACEHOLDER
    }

    /** Raw presence check for the singular-URL shape, matching [hasThumbnail]'s list overload. */
    @JvmStatic
    fun hasThumbnail(url: String?): Boolean = !url.isNullOrBlank()

    // YoutubeService forces hl=zu, so the textual upload date is Zulu; DateWrapper is still reliable, so the date is formatted here.
    @JvmStatic
    fun formatUploadDate(uploadDate: DateWrapper?, textualFallback: String?): String {
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
    fun encodeUrl(url: String?): String {
        return try {
            java.net.URLEncoder.encode(url, "UTF-8")
        } catch (e: Exception) {
            url ?: ""
        }
    }

    // Also escapes the apostrophe: most call sites wrap this in a single-quoted JS literal.
    @JvmStatic
    fun escapeJs(str: String?): String {
        if (str == null) return ""
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    // For raw titles and names used as HTML text (outside wrapInTemplate's title). Also escapes '"' for attributes. Not for getDescription(), which is already HTML.
    @JvmStatic
    fun escapeHtml(str: String?): String {
        if (str == null) return ""
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
