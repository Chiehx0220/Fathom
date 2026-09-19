package org.schabi.newpipe.localserver

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor

// Channel page and playlist page rendering, split out of the former monolithic HtmlRenderer.java.
object HtmlRendererChannel {

    @JvmStatic
    @Throws(Exception::class)
    fun renderChannel(serviceId: Int, channel: ChannelExtractor, activeTab: String, items: List<InfoItem>?, nextPage: Page?, isSubscribed: Boolean, isBlocked: Boolean, isTv: Boolean): String {
        val sb = StringBuilder()
        sb.append(HtmlRendererCommon.getHeaderHtml(serviceId, ""))

        val channelBanner = HtmlRendererCommon.getThumbnailUrl(channel.banners)
        val channelAvatar = HtmlRendererCommon.getThumbnailUrl(channel.avatars)
        val channelAvatarFallback = if (HtmlRendererCommon.hasThumbnail(channel.avatars)) channelAvatar else null
        val channelName = HtmlRendererCommon.escapeHtml(channel.name)
        val subscriberText = if (channel.subscriberCount >= 0) "${channel.subscriberCount} subscribers" else ""
        val channelDesc = channel.description ?: ""

        sb.append("<div class=\"container\">\n")
          .append("  <div class=\"channel-header\">\n")
          .append("    <img class=\"channel-banner\" src=\"$channelBanner\">\n")
          .append("    <div class=\"channel-details\">\n")
          .append("      <img class=\"channel-avatar\" src=\"$channelAvatar\">\n")
          .append("      <div class=\"channel-info-block\">\n")
          .append("        <h1 class=\"channel-name\">$channelName</h1>\n")
          .append("        <span class=\"uploader-subs\">$subscriberText</span>\n")
          .append("        <p class=\"channel-desc\">$channelDesc</p>\n")
          .append("      </div>\n")

        val channelUrlEncoded = HtmlRendererCommon.encodeUrl(channel.linkHandler.url)
        val channelBackUrl = HtmlRendererCommon.encodeUrl("/channel?serviceId=$serviceId&id=${channel.linkHandler.url}")
        val channelUrlJs = HtmlRendererCommon.escapeJs(channel.linkHandler.url)

        sb.append(HtmlRendererCommon.renderSubscribeButton(channel.linkHandler.url, channel.name, channelAvatar, channelBackUrl, isSubscribed))

        // Backed by Flow's native FlowNeuroEngine block list - see nativeBlockChannel() in
        // LocalServerFlowData.kt.
        if (isBlocked) {
            sb.append("      <a href=\"/block_channel?action=unblock&id=$channelUrlEncoded&back=$channelBackUrl\" onclick=\"toggleBlock(event, this, '$channelUrlJs')\" class=\"subscribe-btn blocked\">Blocked</a>\n")
        } else {
            sb.append("      <a href=\"/block_channel?action=block&id=$channelUrlEncoded&back=$channelBackUrl\" onclick=\"toggleBlock(event, this, '$channelUrlJs')\" class=\"subscribe-btn danger\">Block</a>\n")
        }

        val videosActive = if (activeTab == "videos") "active" else ""
        val playlistsActive = if (activeTab == "playlists") "active" else ""

        sb.append("    </div>\n")
          .append("    <div class=\"channel-tabs-selector\">\n")
          .append("      <a href=\"/channel?serviceId=$serviceId&id=${channel.linkHandler.url}&tab=videos\" class=\"channel-tab-btn $videosActive\">Uploads</a>\n")
          .append("      <a href=\"/channel?serviceId=$serviceId&id=${channel.linkHandler.url}&tab=playlists\" class=\"channel-tab-btn $playlistsActive\">Playlists</a>\n")
          .append("    </div>\n")
          .append("  </div>\n")

        if (items != null && items.isNotEmpty()) {
            sb.append(renderChannelItemsFragment(serviceId, channel.linkHandler.url, activeTab, items, nextPage, channelAvatarFallback))
        } else {
            sb.append("<div class=\"loading-placeholder\">No items found under this tab.</div>\n")
        }

        sb.append("</div>\n")
        return HtmlRendererCommon.wrapInTemplate(channel.name, sb.toString(), isTv)
    }

    // Grid + "Load More" for a channel-tab batch - shared by renderChannel() and loadMoreChannel()'s
    // ajax=1 follow-ups.
    @JvmStatic
    fun renderChannelItemsFragment(serviceId: Int, channelUrl: String, activeTab: String, items: List<InfoItem>, nextPage: Page?, fallbackAvatarUrl: String?): String {
        val sb = StringBuilder()
        HtmlRendererCommon.renderGrid(sb, serviceId, items, fallbackAvatarUrl = fallbackAvatarUrl)

        val nextPageJs = HtmlRendererCommon.serializePageJs(nextPage)
        if (nextPageJs != null) {
            val channelUrlJs = HtmlRendererCommon.escapeJs(channelUrl)
            val tabJs = HtmlRendererCommon.escapeJs(activeTab)
            sb.append("  <div class=\"pagination\">\n")
              .append("    <a href=\"#\" class=\"btn-page\" onclick=\"loadMoreChannel(this, '$nextPageJs', $serviceId, '$channelUrlJs', '$tabJs'); return false;\">Load More</a>\n")
              .append("  </div>\n")
        }
        return sb.toString()
    }

    @JvmStatic
    @Throws(Exception::class)
    fun renderPlaylist(serviceId: Int, playlist: PlaylistExtractor, items: List<InfoItem>?, nextPage: Page?, isBookmarked: Boolean, isTv: Boolean): String {
        val sb = StringBuilder()
        sb.append(HtmlRendererCommon.getHeaderHtml(serviceId, ""))

        val playlistName = HtmlRendererCommon.escapeHtml(playlist.name)
        val playlistUploader = HtmlRendererCommon.escapeHtml(playlist.uploaderName)
        val playlistItemCount = if (playlist.streamCount >= 0) "${playlist.streamCount} items" else ""

        sb.append("<div class=\"container\">\n")
          .append("  <div class=\"channel-header\" style=\"padding:28px; display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:16px;\">\n")
          .append("    <div style=\"display:flex; flex-direction:column; gap:6px;\">\n")
          .append("      <h1 class=\"channel-name\">$playlistName</h1>\n")
          .append("      <span class=\"uploader-subs\">Playlist by $playlistUploader • $playlistItemCount</span>\n")
          .append("    </div>\n")

        val playlistUrlEncoded = HtmlRendererCommon.encodeUrl(playlist.linkHandler.url)
        val playlistBackUrl = HtmlRendererCommon.encodeUrl("/playlist?serviceId=$serviceId&id=${playlist.linkHandler.url}")

        if (isBookmarked) {
            sb.append("    <a href=\"/bookmark_playlist?action=unbookmark&id=$playlistUrlEncoded&back=$playlistBackUrl\" class=\"subscribe-btn subscribed\" style=\"text-decoration:none;\">⭐ Bookmarked</a>\n")
        } else {
            val playlistNameEncoded = HtmlRendererCommon.encodeUrl(playlist.name)
            sb.append("    <a href=\"/bookmark_playlist?action=bookmark&id=$playlistUrlEncoded&name=$playlistNameEncoded&back=$playlistBackUrl\" class=\"subscribe-btn\" style=\"text-decoration:none;\">⭐ Bookmark Playlist</a>\n")
        }

        sb.append("  </div>\n")

        if (items != null && items.isNotEmpty()) {
            sb.append(renderPlaylistItemsFragment(serviceId, playlist.linkHandler.url, items, nextPage))
        } else {
            sb.append("<div class=\"loading-placeholder\">No streams in this playlist.</div>\n")
        }

        sb.append("</div>\n")
        return HtmlRendererCommon.wrapInTemplate("Playlist: " + playlist.name, sb.toString(), isTv)
    }

    // Grid + "Load More" for a playlist batch - shared by renderPlaylist() and loadMorePlaylist()'s
    // ajax=1 follow-ups.
    @JvmStatic
    fun renderPlaylistItemsFragment(serviceId: Int, playlistUrl: String, items: List<InfoItem>, nextPage: Page?): String {
        val sb = StringBuilder()
        HtmlRendererCommon.renderGrid(sb, serviceId, items)

        val nextPageJs = HtmlRendererCommon.serializePageJs(nextPage)
        if (nextPageJs != null) {
            val playlistUrlJs = HtmlRendererCommon.escapeJs(playlistUrl)
            sb.append("  <div class=\"pagination\">\n")
              .append("    <a href=\"#\" class=\"btn-page\" onclick=\"loadMorePlaylist(this, '$nextPageJs', $serviceId, '$playlistUrlJs'); return false;\">Load More</a>\n")
              .append("  </div>\n")
        }
        return sb.toString()
    }
}
