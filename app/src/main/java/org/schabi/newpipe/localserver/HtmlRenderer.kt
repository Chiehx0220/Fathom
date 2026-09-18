package org.schabi.newpipe.localserver

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo

// Thin facade over the page-specific HtmlRenderer* files (HtmlRendererCommon, HtmlRendererListings,
// HtmlRendererWatch, HtmlRendererChannel, HtmlRendererSettings) that this file
// used to contain directly. Kept as a facade - rather than
// updating every LocalHttpServer.java/ServerService.java call site to the new class names -
// so every existing "HtmlRenderer.xxx(...)" call in the rest of the app keeps working unchanged.
// lightColors/darkColors stay here (not moved) for the same reason: ServerService.java sets them
// via "HtmlRenderer.lightColors = ...", and HtmlRendererCommon reads them back qualified as
// "HtmlRenderer.lightColors" when building the theme CSS.
object HtmlRenderer {

    @JvmField
    var lightColors: MutableMap<String, String> = HashMap()

    @JvmField
    var darkColors: MutableMap<String, String> = HashMap()

    @JvmStatic
    fun deserializePage(b64: String?): Page? = HtmlRendererCommon.deserializePage(b64)

    @JvmStatic
    fun renderGrid(sb: StringBuilder, serviceId: Int, items: List<InfoItem>) {
        HtmlRendererCommon.renderGrid(sb, serviceId, items)
    }

    @JvmStatic
    fun getThumbnailUrl(thumbnails: List<Image>?): String = HtmlRendererCommon.getThumbnailUrl(thumbnails)

    @JvmStatic
    fun getThumbnailUrl(url: String?): String = HtmlRendererCommon.getThumbnailUrl(url)

    @JvmStatic
    fun renderHomeSkeleton(serviceId: Int, isTv: Boolean): String = HtmlRendererListings.renderHomeSkeleton(serviceId, isTv)

    @JvmStatic
    fun renderHomeFeed(serviceId: Int, items: List<InfoItem>, nextToken: String?): String =
        HtmlRendererListings.renderHomeFeed(serviceId, items, nextToken)

    @JvmStatic
    fun renderSearch(serviceId: Int, query: String, items: List<InfoItem>, nextPage: Page?, isTv: Boolean): String = HtmlRendererListings.renderSearch(serviceId, query, items, nextPage, isTv)

    @JvmStatic
    fun renderSearchResultsFragment(serviceId: Int, query: String, items: List<InfoItem>, nextPage: Page?): String =
        HtmlRendererListings.renderSearchResultsFragment(serviceId, query, items, nextPage)

    @JvmStatic
    fun renderHistory(serviceId: Int, items: List<InfoItem>?, isTv: Boolean): String = HtmlRendererListings.renderHistory(serviceId, items, isTv)

    @JvmStatic
    fun renderWatchLater(serviceId: Int, items: List<InfoItem>?, isTv: Boolean): String = HtmlRendererListings.renderWatchLater(serviceId, items, isTv)

    @JvmStatic
    fun renderSubscriptions(serviceId: Int, channels: List<InfoItem>?, playlists: List<InfoItem>?, watchLater: List<InfoItem>?, activeTab: String, isTv: Boolean): String =
        HtmlRendererListings.renderSubscriptions(serviceId, channels, playlists, watchLater, activeTab, isTv)

    @JvmStatic
    fun renderWatchSkeleton(serviceId: Int, mediaUrl: String?, isTv: Boolean): String = HtmlRendererWatch.renderWatchSkeleton(serviceId, mediaUrl, isTv)

    @JvmStatic
    fun renderWatchContent(serviceId: Int, info: StreamInfo, isSubscribed: Boolean, isWatchLater: Boolean, likeState: String?, isTv: Boolean, targetQuality: String?, duration: Long): String =
        HtmlRendererWatch.renderWatchContent(serviceId, info, isSubscribed, isWatchLater, likeState, isTv, targetQuality, duration)

    @JvmStatic
    fun renderAudioWatch(serviceId: Int, info: StreamInfo, isSubscribed: Boolean, isWatchLater: Boolean, likeState: String?, isTv: Boolean): String =
        HtmlRendererWatch.renderAudioWatch(serviceId, info, isSubscribed, isWatchLater, likeState, isTv)

    @JvmStatic
    @JvmOverloads
    fun renderComments(serviceId: Int, videoUrl: String, items: List<CommentsInfoItem>, nextPage: Page?, isTv: Boolean, isReplies: Boolean = false): String =
        HtmlRendererWatch.renderComments(serviceId, videoUrl, items, nextPage, isTv, isReplies)

    @JvmStatic
    @Throws(Exception::class)
    fun renderChannel(serviceId: Int, channel: ChannelExtractor, activeTab: String, items: List<InfoItem>?, nextPage: Page?, isSubscribed: Boolean, isBlocked: Boolean, isTv: Boolean): String =
        HtmlRendererChannel.renderChannel(serviceId, channel, activeTab, items, nextPage, isSubscribed, isBlocked, isTv)

    @JvmStatic
    fun renderChannelItemsFragment(serviceId: Int, channelUrl: String, activeTab: String, items: List<InfoItem>, nextPage: Page?, fallbackAvatarUrl: String?): String =
        HtmlRendererChannel.renderChannelItemsFragment(serviceId, channelUrl, activeTab, items, nextPage, fallbackAvatarUrl)

    @JvmStatic
    @Throws(Exception::class)
    fun renderPlaylist(serviceId: Int, playlist: PlaylistExtractor, items: List<InfoItem>?, nextPage: Page?, isBookmarked: Boolean, isTv: Boolean): String =
        HtmlRendererChannel.renderPlaylist(serviceId, playlist, items, nextPage, isBookmarked, isTv)

    @JvmStatic
    fun renderPlaylistItemsFragment(serviceId: Int, playlistUrl: String, items: List<InfoItem>, nextPage: Page?): String =
        HtmlRendererChannel.renderPlaylistItemsFragment(serviceId, playlistUrl, items, nextPage)

    @JvmStatic
    fun renderSettings(serviceId: Int, currentQuality: String, hideWatched: Boolean, hideShorts: Boolean, homeFeedMode: String, saved: Boolean, isTv: Boolean): String =
        HtmlRendererSettings.renderSettings(serviceId, currentQuality, hideWatched, hideShorts, homeFeedMode, saved, isTv)
}
