package io.github.aedev.flow.data.paging

import android.util.Log
import androidx.paging.PagingSource
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.Duration
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.UploadDate
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.DistinctKeyTracker
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.ShortsClassifier
import io.github.aedev.flow.utils.SearchFilterResolver
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.resolveNonYouTubeChannelId
import io.github.aedev.flow.utils.resolveNonYouTubePlaylistId
import io.github.aedev.flow.utils.resolveNonYouTubeStreamId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Search path for a service with no InnerTube-shaped endpoint (Bilibili, and any other non-YouTube
 * service) - kept on the service's own NewPipe/PipePipeExtractor search extractor instead of
 * [SearchPageLoader]. None of the YouTube web-client enrichments (Shorts shelf, collab avatar
 * stacks) apply here; [SearchPagingSource] delegates to [load] for every non-YouTube service.
 */
internal object BilibiliSearchLoader {
    private const val TAG = "BilibiliSearchLoader"

    suspend fun load(
        service: StreamingService,
        query: String,
        filter: SearchFilter,
        shortsEnabled: Boolean,
        continuation: String?,
        loadedItemKeys: DistinctKeyTracker,
    ): PagingSource.LoadResult<String, SearchResultItem> {
        // NewPipe search has no dedicated Shorts tab for non-YouTube services.
        if (filter.contentType == ContentType.SHORTS) {
            return PagingSource.LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        return withContext(Dispatchers.IO) {
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
                            val videoId = resolveStreamId(service, item.url)
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
                                channelId = resolveChannelId(service, item.uploaderUrl ?: ""),
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
                                ?.takeIf { it.matchesSearchFilters(filter) }
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
                                    id = resolveChannelId(service, item.url),
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
                                    id = resolvePlaylistId(service, item.url),
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

            Log.d(TAG, "Loaded ${items.size} items | query='$query' | service=${service.serviceId} | nextPage=${infoPage.nextPage != null}")

            PagingSource.LoadResult.Page(
                data = loadedItemKeys.filter(items) { it.identityKey() },
                prevKey = null,
                nextKey = infoPage.nextPage?.url,
            )
        }
    }

    private fun buildContentFilters(filter: SearchFilter): List<String> =
        when (filter.contentType) {
            ContentType.VIDEOS -> listOf("videos")
            ContentType.CHANNELS -> listOf("channels")
            ContentType.PLAYLISTS -> listOf("playlists")
            ContentType.LIVE -> listOf("videos")
            else -> emptyList()
        }

    private fun Video.matchesSearchFilters(filter: SearchFilter): Boolean {
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

    private fun resolveStreamId(
        service: StreamingService,
        url: String,
    ): String = resolveNonYouTubeStreamId(url, service) { url.substringAfterLast("/").substringBefore("?") }

    private fun resolveChannelId(
        service: StreamingService,
        url: String,
    ): String = if (url.isBlank()) extractChannelId(url) else resolveNonYouTubeChannelId(url, service) { extractChannelId(url) }

    private fun resolvePlaylistId(
        service: StreamingService,
        url: String,
    ): String = resolveNonYouTubePlaylistId(url, service) { extractPlaylistId(url) }
}
