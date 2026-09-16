package io.github.aedev.flow.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.Duration
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.UploadDate
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.DistinctKeyTracker
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.ShortsClassifier
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf
import io.github.aedev.flow.innertube.pages.renderer.FeedShelfStyle
import io.github.aedev.flow.innertube.pages.search.SearchHeader
import io.github.aedev.flow.innertube.pages.search.SearchResultsPage
import io.github.aedev.flow.innertube.pages.search.SearchSection
import io.github.aedev.flow.utils.SearchFilterResolver
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.resolveNonYouTubeChannelId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * One InnerTube request per page, and nothing after it.
 *
 * Every filter and sort is a `params` token the server applies, so a page arrives already narrowed
 * and already ordered; the avatars, badges and verification the old path fetched per video are read
 * out of the same response.
 */
class SearchPagingSource(
    private val query: String,
    private val filter: SearchFilter = SearchFilter.DEFAULT,
    private val shortsEnabled: Boolean = true,
    private val serviceId: Int = ServiceList.YouTube.serviceId,
    private val onHeader: (SearchHeader) -> Unit = {},
    private val loadPage: SearchPageLoader = DefaultSearchPageLoader,
    private val blockedChannelIds: suspend () -> Set<String> = { emptySet() },
) : PagingSource<String, SearchResultItem>() {
    companion object {
        private const val TAG = "SearchPagingSource"
    }

    override fun getRefreshKey(state: PagingState<String, SearchResultItem>): String? = null

    private val service = runCatching { NewPipe.getService(serviceId) }.getOrDefault(ServiceList.YouTube)
    private val isYouTube = service.serviceId == ServiceList.YouTube.serviceId
    private val loadedItemKeys = DistinctKeyTracker()

    override suspend fun load(params: LoadParams<String>): LoadResult<String, SearchResultItem> {
        val continuation = params.key
        return try {
            if (!isYouTube) return loadNonYouTubePage(continuation)

            val page = loadPage(query, filter.toSearchParams(), continuation)
            if (continuation == null) onHeader(page.header)
            val results = page.toResultItems(shortsEnabled).withoutBlockedChannels(blockedChannelIds())
            LoadResult.Page(
                data = loadedItemKeys.filter(results) { it.identityKey() },
                prevKey = null,
                nextKey = page.continuation,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    // ── non-YouTube (NewPipe extractor) path ────────────────────────────────

    /**
     * Bilibili (and any other non-YouTube service) has no InnerTube-shaped search endpoint, so it
     * keeps going through the service's own NewPipe search extractor instead of [loadPage]. None of
     * the YouTube web-client enrichments (Shorts shelf, collab avatar stacks) apply to it.
     */
    private suspend fun loadNonYouTubePage(continuation: String?): LoadResult<String, SearchResultItem> =
        withContext(Dispatchers.IO) {
            // NewPipe search has no dedicated Shorts tab for non-YouTube services.
            if (filter.contentType == ContentType.SHORTS) {
                return@withContext LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }

            val extractor =
                service.getSearchExtractor(
                    query,
                    SearchFilterResolver.resolveSearchContentFilters(service, buildContentFilters(filter)),
                    emptyList(),
                )
            extractor.fetchPage()

            val infoPage = if (continuation != null) extractor.getPage(Page(continuation)) else extractor.initialPage

            val items: List<SearchResultItem> =
                infoPage.items.mapNotNull { item ->
                    when (item) {
                        is StreamInfoItem -> {
                            val isLiveStream =
                                item.streamType == StreamType.LIVE_STREAM ||
                                    item.streamType == StreamType.AUDIO_LIVE_STREAM
                            val videoId = resolveStreamId(item.url)
                            val thumbnail = ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, item.thumbnailUrl)
                            val channelThumb =
                                try {
                                    item.uploaderAvatarUrl.orEmpty()
                                } catch (_: Exception) {
                                    ""
                                }

                            Video(
                                id = videoId,
                                title = item.name ?: "",
                                channelName = item.uploaderName ?: "",
                                channelId = resolveChannelId(item.uploaderUrl ?: ""),
                                thumbnailUrl = thumbnail,
                                duration = item.duration.toInt(),
                                viewCount = item.viewCount,
                                uploadDate = item.textualUploadDate ?: "",
                                timestamp = System.currentTimeMillis(),
                                channelThumbnailUrl = channelThumb,
                                channelThumbnailUrls = listOfNotNull(channelThumb.takeIf { it.isNotBlank() }),
                                isShort = ShortsClassifier.isReel(item),
                                isLive = isLiveStream,
                                serviceId = service.serviceId,
                            ).takeIf { shortsEnabled || !it.isShort }
                                ?.takeIf { it.matchesSearchFilters() }
                                ?.let { SearchResultItem.VideoResult(it) }
                        }

                        is ChannelInfoItem -> {
                            val thumb =
                                try {
                                    item.thumbnailUrl ?: ""
                                } catch (_: Exception) {
                                    ""
                                }

                            SearchResultItem.ChannelResult(
                                Channel(
                                    id = resolveChannelId(item.url),
                                    name = item.name ?: "",
                                    thumbnailUrl = thumb,
                                    subscriberCount = item.subscriberCount,
                                    description = item.description ?: "",
                                    url = item.url ?: "",
                                    serviceId = service.serviceId,
                                ),
                            )
                        }

                        is PlaylistInfoItem -> {
                            val thumb =
                                try {
                                    item.thumbnailUrl ?: ""
                                } catch (_: Exception) {
                                    ""
                                }

                            SearchResultItem.PlaylistResult(
                                Playlist(
                                    id = resolvePlaylistId(item.url),
                                    name = item.name ?: "",
                                    thumbnailUrl = thumb,
                                    videoCount = item.streamCount.toInt(),
                                    serviceId = service.serviceId,
                                ),
                            )
                        }

                        else -> null
                    }
                }

            Log.d(TAG, "Loaded ${items.size} items | query='$query' | service=$serviceId | nextPage=${infoPage.nextPage != null}")

            LoadResult.Page(
                data = loadedItemKeys.filter(items) { it.identityKey() },
                prevKey = null,
                nextKey = infoPage.nextPage?.url,
            )
        }

    private fun buildContentFilters(filter: SearchFilter): List<String> =
        when (filter.contentType) {
            ContentType.VIDEOS -> listOf("videos")
            ContentType.CHANNELS -> listOf("channels")
            ContentType.PLAYLISTS -> listOf("playlists")
            ContentType.LIVE -> listOf("videos")
            else -> emptyList()
        }

    private fun Video.matchesSearchFilters(): Boolean {
        if (filter.contentType == ContentType.LIVE && !isLive) return false
        if (filter.duration == Duration.UNDER_3_MINUTES && duration >= 180) return false
        if (filter.duration == Duration.THREE_TO_20_MINUTES && duration !in 180..1_200) return false
        if (filter.duration == Duration.OVER_20_MINUTES && duration <= 1_200) return false
        if (filter.uploadDate == UploadDate.ANY || uploadDate.isEmpty()) return true

        val loweredDate = uploadDate.lowercase()
        val isMinutesOrSeconds = listOf("second", "minute").any(loweredDate::contains)
        val isHours = loweredDate.contains("hour")
        val isWeeks = loweredDate.contains("week")
        val isMonths = loweredDate.contains("month")
        val isYears = loweredDate.contains("year")
        return when (filter.uploadDate) {
            UploadDate.LAST_HOUR -> isMinutesOrSeconds || loweredDate.contains("1 hour")
            UploadDate.TODAY -> isMinutesOrSeconds || isHours || loweredDate.contains("1 day")
            UploadDate.THIS_WEEK -> !isYears && !isMonths && (!isWeeks || loweredDate.contains("1 week"))
            UploadDate.THIS_MONTH -> !isYears && (!isMonths || loweredDate.contains("1 month"))
            UploadDate.THIS_YEAR -> !isYears || loweredDate.contains("1 year")
            UploadDate.ANY -> true
        }
    }

    private fun extractChannelId(url: String): String =
        url
            .substringAfter("/channel/")
            .substringBefore("/")
            .substringBefore("?")
            .ifEmpty { url.substringAfterLast("/").substringBefore("?") }

    private fun extractPlaylistId(url: String): String =
        url
            .substringAfter("list=")
            .substringBefore("&")
            .ifEmpty { url.substringAfterLast("/").substringBefore("?") }

    private fun resolveStreamId(url: String): String =
        runCatching { service.streamLHFactory.getId(url) }.getOrDefault(url.substringAfterLast("/").substringBefore("?"))

    private fun resolveChannelId(url: String): String =
        if (url.isBlank()) extractChannelId(url) else resolveNonYouTubeChannelId(url, service) { extractChannelId(url) }

    private fun resolvePlaylistId(url: String): String =
        runCatching { service.playlistLHFactory.getId(url) }.getOrDefault(extractPlaylistId(url))
}

fun interface SearchPageLoader {
    suspend operator fun invoke(
        query: String,
        params: String?,
        continuation: String?,
    ): SearchResultsPage
}

private val DefaultSearchPageLoader =
    SearchPageLoader { query, params, continuation ->
        YouTube.videoSearch(query, params, continuation).getOrThrow()
    }

/**
 * YouTube returns the creator's "Latest from" strip as its own shelf directly after the channel
 * card and renders the two as one block, so they are folded together here rather than reaching the
 * grid as two rows that have to find each other again.
 */
internal fun SearchResultsPage.toResultItems(shortsEnabled: Boolean): List<SearchResultItem> {
    val items = mutableListOf<SearchResultItem>()
    var index = 0
    while (index < sections.size) {
        val item =
            when (val section = sections[index]) {
                is SearchSection.Result -> section.item.toResultItem(shortsEnabled)
                is SearchSection.Strip -> section.shelf.toShelfItem(shortsEnabled)
            }
        val latest = (item as? SearchResultItem.ChannelResult)?.let { sections.latestStripAfter(index) }
        if (latest != null) {
            items +=
                (item as SearchResultItem.ChannelResult).copy(
                    latestTitle = latest.title,
                    latestVideos = latest.videos,
                )
            index += 2
            continue
        }
        item?.let(items::add)
        index++
    }
    return items
}

/**
 * Drops everything a blocked creator put in the results, the way the home feed already drops them
 * before ranking: their own card, their videos, and their videos inside a strip. A strip left with
 * nothing goes too, rather than staying as a heading over a gap.
 *
 * Community posts carry no channel id in the response, so a blocked creator's post survives here.
 */
internal fun List<SearchResultItem>.withoutBlockedChannels(blockedChannelIds: Set<String>): List<SearchResultItem> {
    if (blockedChannelIds.isEmpty()) return this

    fun blocked(channelId: String) = channelId.isNotBlank() && channelId in blockedChannelIds
    return mapNotNull { item ->
        when (item) {
            is SearchResultItem.VideoResult -> {
                item.takeUnless { blocked(it.video.channelId) }
            }

            is SearchResultItem.ChannelResult -> {
                item.takeUnless { blocked(it.channel.id) }
            }

            is SearchResultItem.PlaylistResult -> {
                item
            }

            is SearchResultItem.ShelfResult -> {
                val videos = item.videos.filterNot { blocked(it.channelId) }
                when {
                    videos.isNotEmpty() -> item.copy(videos = videos)
                    item.posts.isNotEmpty() -> item
                    else -> null
                }
            }
        }
    }
}

/** The videos strip that immediately follows [index], if that is what the next section holds. */
private fun List<SearchSection>.latestStripAfter(index: Int): SearchResultItem.ShelfResult? =
    (getOrNull(index + 1) as? SearchSection.Strip)
        ?.shelf
        ?.toShelfItem(shortsEnabled = true)
        ?.let { it as? SearchResultItem.ShelfResult }
        ?.takeIf { it.kind == SearchShelfKind.VIDEOS }

private fun FeedItem.toResultItem(shortsEnabled: Boolean): SearchResultItem? =
    when (this) {
        is FeedItem.VideoItem -> SearchResultItem.VideoResult(video)
        is FeedItem.ShortItem -> SearchResultItem.VideoResult(video).takeIf { shortsEnabled }
        is FeedItem.PlaylistItem -> SearchResultItem.PlaylistResult(playlist)
        is FeedItem.RelatedChannelItem -> SearchResultItem.ChannelResult(channel)
        is FeedItem.PostItem -> null
    }

private fun FeedShelf.toShelfItem(shortsEnabled: Boolean): SearchResultItem? {
    val posts = items.filterIsInstance<FeedItem.PostItem>().map { it.post }
    if (posts.isNotEmpty()) {
        return SearchResultItem.ShelfResult(id, title, SearchShelfKind.POSTS, posts = posts)
    }
    val videos = items.mapNotNull { it.shelfVideo() }
    if (videos.isEmpty()) return null
    val kind = if (style == FeedShelfStyle.Grid) SearchShelfKind.SHORTS else SearchShelfKind.VIDEOS
    if (kind == SearchShelfKind.SHORTS && !shortsEnabled) return null
    return SearchResultItem.ShelfResult(
        id = id,
        title = title,
        kind = kind,
        videos = videos,
        collapsedItemCount = collapsedItemCount,
    )
}

private fun FeedItem.shelfVideo(): Video? =
    when (this) {
        is FeedItem.VideoItem -> video
        is FeedItem.ShortItem -> video
        else -> null
    }

private fun SearchResultItem.identityKey(): String =
    when (this) {
        is SearchResultItem.VideoResult -> video.id.prefixed("video")
        is SearchResultItem.ChannelResult -> channel.id.prefixed("channel")
        is SearchResultItem.PlaylistResult -> playlist.id.prefixed("playlist")
        is SearchResultItem.ShelfResult -> "shelf:$id"
    }

private fun String.prefixed(type: String): String = takeIf(String::isNotBlank)?.let { "$type:$it" }.orEmpty()
