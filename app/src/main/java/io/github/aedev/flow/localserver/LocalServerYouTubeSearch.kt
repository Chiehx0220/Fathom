package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.paging.SearchResultItem
import io.github.aedev.flow.data.paging.toResultItems
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem

/**
 * YouTube's search results through the app's own InnerTube search, so the page lists what the app's search
 * lists and follows the app's fixes instead of the extractor's.
 *
 * The continuation of a page is a [Page] whose url holds "yt:s:<token>"; a page from the extractor has no such
 * prefix, so it keeps going there. Only the plain search runs here: shelves (Shorts, posts) are left out, which is
 * also what the extractor lists.
 */
internal object LocalServerYouTubeSearch {
    private const val MORE = "yt:s:"
    private const val YOUTUBE = 0
    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    private val PLAYLIST_ID = Regex("(PL|OLAK5uy_|RD|UU|FL)[A-Za-z0-9_-]+")

    /** True for a continuation this object made, so [LocalServerSource] does not hand it to the extractor. */
    fun owns(page: Page?): Boolean = page?.url?.startsWith(MORE) == true

    /** One page, or null when the extractor should answer: a page of its own, a failure, or nothing found on a first page. */
    fun search(
        query: String,
        nextPage: Page?,
    ): ListPage? {
        if (nextPage != null && !owns(nextPage)) return null
        return try {
            runBlocking {
                val page = YouTube.videoSearch(query, null, nextPage?.url?.removePrefix(MORE)).getOrThrow()
                val items = page.toResultItems(shortsEnabled = true).mapNotNull { it.toInfoItem() }
                if (items.isEmpty() && nextPage == null) {
                    null
                } else {
                    ListPage(items, page.continuation?.let { Page(MORE + it) })
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            serverLog("InnerTube search failed: ${e.message}")
            // A continuation that fails ends the list; a first page that fails goes to the extractor.
            if (owns(nextPage)) ListPage(emptyList(), null) else null
        }
    }

    /** A "Course" arrives as a video whose id is a playlist's; opened as a video it would not play. */
    private fun Video.asPlaylist(): InfoItem? =
        if (PLAYLIST_ID.matches(id)) {
            PlaylistInfoItem(YOUTUBE, playlistIdToUrl(id), title).also {
                if (thumbnailUrl.isNotBlank()) it.thumbnailUrl = thumbnailUrl
                it.uploaderName = channelName
            }
        } else {
            null
        }

    private fun SearchResultItem.toInfoItem(): InfoItem? =
        when (this) {
            is SearchResultItem.VideoResult -> {
                if (VIDEO_ID.matches(video.id)) video.toStreamInfoItem(YOUTUBE) else video.asPlaylist()
            }

            is SearchResultItem.ChannelResult -> {
                ChannelInfoItem(YOUTUBE, channelIdToUrl(channel.id), channel.name).also {
                    if (channel.thumbnailUrl.isNotBlank()) it.thumbnailUrl = channel.thumbnailUrl
                    it.subscriberCount = channel.subscriberCount
                    if (channel.description.isNotBlank()) it.description = channel.description
                }
            }

            is SearchResultItem.PlaylistResult -> {
                PlaylistInfoItem(YOUTUBE, playlistIdToUrl(playlist.id), playlist.name).also {
                    if (playlist.thumbnailUrl.isNotBlank()) it.thumbnailUrl = playlist.thumbnailUrl
                    it.streamCount = playlist.videoCount.toLong()
                }
            }

            is SearchResultItem.ShelfResult -> {
                null
            }
        }
}
