package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page

/** The channel page and the playlist page. */
object HtmlRendererChannel {
    @JvmStatic
    @Throws(Exception::class)
    fun renderChannel(
        serviceId: Int,
        channel: ChannelHeader,
        activeTab: String,
        items: List<InfoItem>?,
        nextPage: Page?,
        isSubscribed: Boolean,
        isBlocked: Boolean,
        isTv: Boolean,
    ): String {
        val banner = HtmlRendererCommon.getThumbnailUrl(channel.bannerUrl)
        val avatar = HtmlRendererCommon.getThumbnailUrl(channel.avatarUrl)
        val subscribers = if (channel.subscriberCount >= 0) "${channel.subscriberCount} subscribers" else ""
        val back = WebUi.enc("/channel?serviceId=$serviceId&id=${channel.url}")

        fun tab(
            key: String,
            label: String,
        ): String {
            val active = if (key == activeTab) " active" else ""
            return "<a class=\"channel-tab$active\" href=\"/channel?serviceId=$serviceId&id=${WebUi.esc(channel.url)}&tab=$key\">$label</a>\n"
        }

        val sb = StringBuilder(WebShell.header(serviceId, ""))
        sb.append("<main class=\"container\">\n")
          .append("<section class=\"channel-header\">\n")
          .append("  <img class=\"channel-banner\" src=\"${WebUi.esc(banner)}\" alt=\"\">\n")
          .append("  <div class=\"channel-details\">\n")
          .append("    ${WebUi.avatar(channel.name, avatar, "xl")}\n")
          .append("    <div class=\"channel-info\">\n")
          .append("      <h1 class=\"channel-name\">${WebUi.esc(channel.name)}</h1>\n")
          .append("      <span class=\"channel-subs\">${WebUi.esc(subscribers)}</span>\n")
          .append("      <p class=\"channel-desc\">${WebUi.esc(channel.description)}</p>\n")
          .append("    </div>\n")
          .append("    <div class=\"channel-actions\">\n")
          .append(WebUi.subscribeButton(channel.url, channel.name, avatar, back, isSubscribed))
          .append(WebUi.blockButton(channel.url, back, isBlocked))
          .append("    </div>\n")
          .append("  </div>\n")
          .append("  <div class=\"channel-tabs\">\n")
          .append(tab("videos", "Uploads"))
          .append(tab("playlists", "Playlists"))
          .append("  </div>\n")
          .append("</section>\n")

        if (items.isNullOrEmpty()) {
            sb.append(WebUi.notice("No items found under this tab."))
        } else {
            sb.append(renderChannelItemsFragment(serviceId, channel.url, activeTab, items, nextPage, avatar.takeIf { channel.avatarUrl != null }))
        }
        sb.append("</main>\n")
        return WebShell.page(channel.name, sb.toString(), isTv)
    }

    /** A batch of a channel's uploads or playlists with its "Load more". */
    @JvmStatic
    fun renderChannelItemsFragment(
        serviceId: Int,
        channelUrl: String,
        activeTab: String,
        items: List<InfoItem>,
        nextPage: Page?,
        fallbackAvatarUrl: String?,
    ): String {
        val next =
            HtmlRendererCommon.serializePage(nextPage)?.let {
                "/channel?ajax=1&serviceId=$serviceId&id=${WebUi.enc(channelUrl)}&tab=${WebUi.enc(activeTab)}&nextPage=${WebUi.enc(it)}"
            }
        return WebUi.grid(serviceId, items, fallbackAvatarUrl = fallbackAvatarUrl) + WebUi.loadMore(next)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun renderPlaylist(
        serviceId: Int,
        playlist: PlaylistHeader,
        items: List<InfoItem>?,
        nextPage: Page?,
        isBookmarked: Boolean,
        isTv: Boolean,
    ): String {
        val count = if (playlist.streamCount >= 0) "${playlist.streamCount} items" else ""
        val back = WebUi.enc("/playlist?serviceId=$serviceId&id=${playlist.url}")
        val id = WebUi.enc(playlist.url)
        val bookmark =
            if (isBookmarked) {
                WebUi.button("Bookmarked", "star", "tonal", href = "/bookmark_playlist?action=unbookmark&id=$id&back=$back")
            } else {
                WebUi.button("Bookmark playlist", "star", "filled", href = "/bookmark_playlist?action=bookmark&id=$id&name=${WebUi.enc(playlist.name)}&back=$back")
            }

        val sb = StringBuilder(WebShell.header(serviceId, ""))
        sb.append("<main class=\"container\">\n")
          .append("<section class=\"channel-header playlist-header\">\n")
          .append("  <div class=\"channel-info\">\n")
          .append("    <h1 class=\"channel-name\">${WebUi.esc(playlist.name)}</h1>\n")
          .append("    <span class=\"channel-subs\">Playlist by ${WebUi.esc(playlist.uploaderName)} • $count</span>\n")
          .append("  </div>\n")
          .append("  <div class=\"channel-actions\">$bookmark</div>\n")
          .append("</section>\n")
        if (items.isNullOrEmpty()) {
            sb.append(WebUi.notice("No streams in this playlist."))
        } else {
            sb.append(renderPlaylistItemsFragment(serviceId, playlist.url, items, nextPage))
        }
        sb.append("</main>\n")
        return WebShell.page("Playlist: ${playlist.name}", sb.toString(), isTv)
    }

    /** A batch of a playlist's videos with its "Load more". */
    @JvmStatic
    fun renderPlaylistItemsFragment(
        serviceId: Int,
        playlistUrl: String,
        items: List<InfoItem>,
        nextPage: Page?,
    ): String {
        val next =
            HtmlRendererCommon.serializePage(nextPage)?.let {
                "/playlist?ajax=1&serviceId=$serviceId&id=${WebUi.enc(playlistUrl)}&nextPage=${WebUi.enc(it)}"
            }
        return WebUi.grid(serviceId, items) + WebUi.loadMore(next)
    }
}
