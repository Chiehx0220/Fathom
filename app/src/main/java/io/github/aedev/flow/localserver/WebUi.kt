package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * The building blocks every page of the web UI is made from. A page never writes styles or scripts inline: it
 * assembles these, and the stylesheet (assets/web/css) and scripts (assets/web/js) do the rest. Behaviour is
 * declared with `data-action` and friends, and handled in one place (js/actions.js).
 */
object WebUi {
    fun esc(text: String?): String = HtmlRendererCommon.escapeHtml(text)

    fun enc(text: String?): String = HtmlRendererCommon.encodeUrl(text)

    /** A glyph from the Material Symbols font. */
    fun icon(name: String): String = "<span class=\"material-symbols-rounded\">$name</span>"

    // ---- Page furniture -------------------------------------------------------------------------------------

    /** The heading row at the top of a page, with optional action buttons on its right. */
    fun pageTitle(
        iconName: String?,
        title: String,
        actions: String = "",
    ): String {
        val glyph = iconName?.let { icon(it) }.orEmpty()
        val actionsHtml = if (actions.isEmpty()) "" else "<div class=\"page-actions\">$actions</div>"
        return "<div class=\"page-head\"><h1 class=\"page-title\">$glyph${esc(title)}</h1>$actionsHtml</div>\n"
    }

    /** A message in place of content: an empty list, or a failure. */
    fun notice(
        text: String,
        error: Boolean = false,
    ): String = "<div class=\"notice${if (error) " notice-error" else ""}\">${esc(text)}</div>\n"

    fun spinner(text: String): String = "<div class=\"loading\"><div class=\"spinner\"></div><div class=\"loading-text\">${esc(text)}</div></div>\n"

    /** A region that fetches its own content once the page is up; it shows a spinner until then. */
    fun asyncRegion(
        url: String,
        loadingText: String,
    ): String = "<div class=\"async\" data-fill=\"${esc(url)}\">${spinner(loadingText)}</div>\n"

    /** "Load more" under a list. The response to [url] replaces this whole element, so it carries its own next one. */
    fun loadMore(
        url: String?,
        label: String = "Load more",
    ): String {
        if (url == null) return ""
        return "<div class=\"pagination\"><button type=\"button\" class=\"btn\" data-action=\"load-more\" data-url=\"${esc(url)}\">${esc(label)}</button></div>\n"
    }

    // ---- Data for scripts ------------------------------------------------------------------------------------

    /** A JSON string literal. `<`, `>` and `&` are escaped so the text can sit inside a <script> without ending it. */
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

    /** A block of JSON for the page's scripts to read (js/player.js reads the watch page's this way), so a page never writes script code. */
    fun jsonData(
        id: String,
        json: String,
    ): String = "<script type=\"application/json\" id=\"$id\">$json</script>\n"

    /** A related video: picture beside its title, uploader and figures. [href] is where it opens. */
    fun compactCard(
        href: String,
        thumbnailUrl: String,
        title: String,
        uploader: String,
        meta: String,
    ): String {
        val link = esc(href)
        return "<article class=\"card card-compact\">\n" +
            "  <div class=\"card-media\"><a class=\"card-media-link\" href=\"$link\" tabindex=\"-1\"><img class=\"card-thumb\" src=\"${esc(thumbnailUrl)}\" alt=\"\" loading=\"lazy\"></a></div>\n" +
            "  <div class=\"card-info\"><a class=\"card-title\" href=\"$link\">${esc(title)}</a>" +
            "<div class=\"card-meta\"><span class=\"card-uploader\">${esc(uploader)}</span>${if (meta.isEmpty()) "" else "<span>${esc(meta)}</span>"}</div></div>\n" +
            "</article>\n"
    }

    // ---- Buttons --------------------------------------------------------------------------------------------

    /**
     * A button, or a link when [href] is given. [variant] is `tonal` (default), `filled`, `text` or `danger`.
     * [attrs] is raw, already-escaped attribute text (for `data-*`).
     */
    fun button(
        label: String,
        iconName: String? = null,
        variant: String = "tonal",
        attrs: String = "",
        href: String? = null,
    ): String {
        val inner = (iconName?.let { icon(it) }.orEmpty()) + esc(label)
        val cls = "btn btn-$variant"
        return if (href != null) {
            "<a class=\"$cls\" href=\"${esc(href)}\" $attrs>$inner</a>"
        } else {
            "<button type=\"button\" class=\"$cls\" $attrs>$inner</button>"
        }
    }

    /**
     * A two-state button (subscribed / not, saved / not, ...). It shows one of two labels depending on `is-on`, which
     * the script flips; nothing rewrites its text. [href] is the no-script fallback that does the same on the server.
     */
    fun toggleButton(
        action: String,
        on: Boolean,
        onLabel: String,
        offLabel: String,
        onIcon: String?,
        offIcon: String?,
        href: String,
        data: Map<String, String>,
        variant: String = "",
    ): String {
        val attrs = data.entries.joinToString(" ") { (k, v) -> "data-$k=\"${esc(v)}\"" }
        val cls = "btn btn-toggle ${if (variant.isEmpty()) "" else "btn-$variant "}${if (on) "is-on" else ""}".trim()
        fun face(
            iconName: String?,
            label: String,
        ) = (iconName?.let { icon(it) }.orEmpty()) + esc(label)
        return "<a class=\"$cls\" href=\"${esc(href)}\" data-action=\"$action\" $attrs>" +
            "<span class=\"when-on\">${face(onIcon, onLabel)}</span><span class=\"when-off\">${face(offIcon, offLabel)}</span></a>\n"
    }

    // ---- Actions on a channel or a video ----------------------------------------------------------------------

    /** Subscribe / Subscribed. [backEncoded] is where the no-script fallback link returns to. */
    fun subscribeButton(
        uploaderUrl: String,
        uploaderName: String,
        avatarUrl: String,
        backEncoded: String,
        subscribed: Boolean,
    ): String {
        val id = enc(uploaderUrl)
        val href =
            if (subscribed) {
                "/subscribe?action=unsubscribe&id=$id&back=$backEncoded"
            } else {
                "/subscribe?action=subscribe&id=$id&name=${enc(uploaderName)}&avatar=${enc(avatarUrl)}&back=$backEncoded"
            }
        return toggleButton(
            "subscribe", subscribed, "Subscribed", "Subscribe", null, null, href,
            mapOf("id" to uploaderUrl, "name" to uploaderName, "avatar" to avatarUrl), variant = "subscribe",
        )
    }

    /** Block / Blocked, backed by the app's own channel block list. */
    fun blockButton(
        channelUrl: String,
        backEncoded: String,
        blocked: Boolean,
    ): String {
        val action = if (blocked) "unblock" else "block"
        return toggleButton(
            "block", blocked, "Blocked", "Block", null, null,
            "/block_channel?action=$action&id=${enc(channelUrl)}&back=$backEncoded", mapOf("id" to channelUrl), variant = "block",
        )
    }

    /** Watch later / Saved, for the video being watched. */
    fun watchLaterButton(
        info: StreamInfo,
        serviceId: Int,
        saved: Boolean,
    ): String {
        val thumb = HtmlRendererCommon.getThumbnailUrl(info.thumbnails)
        val action = if (saved) "remove" else "add"
        val href =
            "/watch_later_action?action=$action&url=${enc(info.url)}&title=${enc(info.name)}&uploader=${enc(info.uploaderName)}" +
                "&thumbnail=${enc(thumb)}&type=video&serviceId=$serviceId&uploaderUrl=${enc(info.uploaderUrl)}"
        return toggleButton(
            "watch-later", saved, "Saved", "Watch Later", "check", "schedule", href,
            videoData(info, thumb) + mapOf("service" to serviceId.toString()),
        )
    }

    /** Like (and, unless [includeDislike] is false, dislike) as one pill. [likeState] is LIKED, DISLIKED or anything else for neither. */
    fun likeDislike(
        info: StreamInfo,
        likeState: String?,
        includeDislike: Boolean = true,
    ): String {
        val thumb = HtmlRendererCommon.getThumbnailUrl(info.thumbnails)
        val data = videoData(info, thumb).entries.joinToString(" ") { (k, v) -> "data-$k=\"${esc(v)}\"" }
        val likes = if (info.likeCount >= 0) HtmlRendererCommon.formatCount(info.likeCount) else "Like"
        val likeOn = if (likeState == "LIKED") " active" else ""
        val dislikeOn = if (likeState == "DISLIKED") " active" else ""
        val dislike =
            if (includeDislike) {
                "<span class=\"pill-divider\"></span><button type=\"button\" class=\"pill-btn dislike-btn$dislikeOn\" data-action=\"dislike\" $data aria-label=\"Dislike\">${icon("thumb_down")}</button>"
            } else {
                ""
            }
        return "<div class=\"like-dislike-pill\"><button type=\"button\" class=\"pill-btn like-btn$likeOn\" data-action=\"like\" $data>${icon("thumb_up")}${esc(likes)}</button>$dislike</div>\n"
    }

    private fun videoData(
        info: StreamInfo,
        thumb: String,
    ): Map<String, String> =
        mapOf("url" to info.url, "title" to info.name, "uploader" to info.uploaderName, "thumbnail" to thumb, "uploader-url" to info.uploaderUrl)

    // ---- People and cards ------------------------------------------------------------------------------------

    /** A round avatar: the picture if there is one, and if it fails to load (or there is none), a coloured initial. */
    fun avatar(
        name: String,
        imageUrl: String?,
        size: String = "",
    ): String {
        val picture = if (imageUrl.isNullOrBlank()) "" else "<img src=\"${esc(HtmlRendererCommon.getThumbnailUrl(imageUrl))}\" alt=\"\" loading=\"lazy\">"
        val initial = HtmlRendererCommon.avatarInitial(name)
        val extra = if (size.isEmpty()) "" else " avatar-$size"
        return "<span class=\"avatar$extra\" style=\"--avatar-bg:${HtmlRendererCommon.avatarColorFor(name)}\">$picture<span class=\"avatar-initial\">${esc(initial)}</span></span>"
    }

    /**
     * Every item of a listing, as a grid. Channels are list rows, everything else is a card. The page's [serviceId] only
     * applies when an item's own service cannot be told from its URL (a library mixes YouTube and Bilibili items).
     */
    fun grid(
        serviceId: Int,
        items: List<InfoItem>,
        removable: Boolean = false,
        fallbackAvatarUrl: String? = null,
    ): String {
        val sb = StringBuilder("<div class=\"grid\">\n")
        for (item in items) {
            val itemService = HtmlRendererCommon.serviceOf(serviceId, item.url)
            sb.append(
                when (item.infoType) {
                    InfoItem.InfoType.CHANNEL -> channelRow(itemService, item)
                    else -> card(itemService, item, removable, fallbackAvatarUrl)
                },
            )
        }
        return sb.append("</div>\n").toString()
    }

    private fun linkFor(
        serviceId: Int,
        item: InfoItem,
    ): String =
        when (item.infoType) {
            InfoItem.InfoType.PLAYLIST -> "/playlist?serviceId=$serviceId&id=${item.url}"
            InfoItem.InfoType.CHANNEL -> "/channel?serviceId=$serviceId&id=${item.url}"
            else -> "/watch?serviceId=$serviceId&id=${item.url}"
        }

    private fun channelRow(
        serviceId: Int,
        item: InfoItem,
    ): String {
        val name = item.name ?: "Channel"
        val link = esc(linkFor(serviceId, item))
        val subscribers =
            (item as? ChannelInfoItem)?.subscriberCount?.takeIf { it >= 0 }?.let { "${HtmlRendererCommon.formatCount(it)} subscribers" } ?: "Channel"
        val picture = item.thumbnailUrl?.takeIf { HtmlRendererCommon.hasThumbnail(it) }
        return "<article class=\"card card-row\">\n" +
            "  <a class=\"card-row-avatar\" href=\"$link\" tabindex=\"-1\">${avatar(name, picture, "lg")}</a>\n" +
            "  <div class=\"card-info\"><a class=\"card-title\" href=\"$link\">${esc(name)}</a>" +
            "<div class=\"card-meta\"><span class=\"badge\">${icon("person")}${esc(subscribers)}</span></div></div>\n" +
            "</article>\n"
    }

    private fun card(
        serviceId: Int,
        item: InfoItem,
        removable: Boolean,
        fallbackAvatarUrl: String?,
    ): String {
        val link = esc(linkFor(serviceId, item))
        val stream = item as? StreamInfoItem
        val uploader = stream?.uploaderName ?: item.name.orEmpty()
        val avatarUrl = stream?.uploaderAvatarUrl?.takeIf { HtmlRendererCommon.hasThumbnail(it) } ?: fallbackAvatarUrl

        val sb = StringBuilder("<article class=\"card\">\n")
        sb.append("  <div class=\"card-media\">\n")
          .append("    <a class=\"card-media-link\" href=\"$link\" tabindex=\"-1\"><img class=\"card-thumb\" src=\"${esc(HtmlRendererCommon.getThumbnailUrl(item.thumbnailUrl))}\" alt=\"\" loading=\"lazy\"></a>\n")
        if (removable) {
            // Overlay controls sit beside the link, not inside it: a control inside a link would also follow the link.
            sb.append("    <button type=\"button\" class=\"card-remove\" data-action=\"history-remove\" data-url=\"${esc(item.url)}\" data-service=\"$serviceId\" aria-label=\"Remove from history\">${icon("delete")}</button>\n")
              .append("    <label class=\"card-select\"><input type=\"checkbox\" class=\"card-select-checkbox\" data-url=\"${esc(item.url)}\" aria-label=\"Select for batch delete\">${icon("check")}</label>\n")
        }
        sb.append("  </div>\n")
          .append("  <div class=\"card-body\">\n")
          .append("    ${avatar(uploader, avatarUrl)}\n")
          .append("    <div class=\"card-info\">\n")
          .append("      <a class=\"card-title\" href=\"$link\">${esc(item.name)}</a>\n")
          .append("      <div class=\"card-meta\">\n")
        when {
            item.infoType == InfoItem.InfoType.PLAYLIST -> sb.append("        <span class=\"badge\">${icon("playlist_play")}Playlist</span>\n")
            stream != null -> {
                val views = if (stream.viewCount >= 0) "${HtmlRendererCommon.formatCount(stream.viewCount)} views" else "Live / Dynamic"
                val date = HtmlRendererCommon.formatUploadDate(stream.uploadDate, stream.textualUploadDate ?: "")
                sb.append("        <a class=\"card-uploader\" href=\"/channel?serviceId=$serviceId&id=${esc(stream.uploaderUrl)}\">${esc(stream.uploaderName)}</a>\n")
                  .append("        <span>${esc(views)} • ${esc(date)}</span>\n")
            }
            else -> sb.append("        <span class=\"card-uploader\">${esc(item.name)}</span>\n")
        }
        return sb.append("      </div>\n    </div>\n  </div>\n</article>\n").toString()
    }
}
