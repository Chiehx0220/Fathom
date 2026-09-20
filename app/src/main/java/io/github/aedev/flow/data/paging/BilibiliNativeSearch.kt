package io.github.aedev.flow.data.paging

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import androidx.paging.PagingSource
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliSearchItem
import io.github.aedev.flow.bilibili.BilibiliSearchType
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.Duration
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.UploadDate
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.DistinctKeyTracker
import io.github.aedev.flow.player.stream.BilibiliVideoMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

/**
 * Bilibili search through the native client. The paging key is the next page number as a string.
 */
internal object BilibiliNativeSearch {
    private const val SECONDS_PER_HOUR = 3_600L

    suspend fun load(
        api: BilibiliApi,
        query: String,
        filter: SearchFilter,
        continuation: String?,
        loadedItemKeys: DistinctKeyTracker,
    ): PagingSource.LoadResult<String, SearchResultItem> {
        val type =
            when (filter.contentType) {
                ContentType.ALL, ContentType.VIDEOS -> BilibiliSearchType.VIDEO
                ContentType.CHANNELS -> BilibiliSearchType.USER
                // Bilibili has no shorts, playlist or live tab in this client yet.
                else -> return PagingSource.LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
            }
        val page = continuation?.toIntOrNull() ?: 1
        val result = withContext(Dispatchers.IO) { api.search(query, type, page) }
        val nowSec = System.currentTimeMillis() / 1000
        val items =
            result.items.mapNotNull { item ->
                when (item) {
                    is BilibiliSearchItem.Video ->
                        BilibiliVideoMapper.videoFromSearch(item).takeIf { matches(item, filter, nowSec) }?.let { SearchResultItem.VideoResult(it) }
                    is BilibiliSearchItem.User -> SearchResultItem.ChannelResult(item.toChannel())
                }
            }
        return PagingSource.LoadResult.Page(
            data = loadedItemKeys.filter(items) { it.identityKey() },
            prevKey = null,
            nextKey = if (result.hasMore) (page + 1).toString() else null,
        )
    }

    private fun matches(
        video: BilibiliSearchItem.Video,
        filter: SearchFilter,
        nowSec: Long,
    ): Boolean {
        val duration = video.durationSec
        val durationOk =
            when (filter.duration) {
                Duration.UNDER_3_MINUTES -> duration < 180
                Duration.THREE_TO_20_MINUTES -> duration in 180..1_200
                Duration.OVER_20_MINUTES -> duration > 1_200
                else -> true
            }
        if (!durationOk) return false
        val ageSec = nowSec - video.uploadTimeSec
        val maxAgeHours =
            when (filter.uploadDate) {
                UploadDate.LAST_HOUR -> 1L
                UploadDate.TODAY -> 24L
                UploadDate.THIS_WEEK -> 24L * 7
                UploadDate.THIS_MONTH -> 24L * 31
                UploadDate.THIS_YEAR -> 24L * 366
                else -> return true
            }
        return video.uploadTimeSec <= 0 || ageSec <= maxAgeHours * SECONDS_PER_HOUR
    }

    private fun BilibiliSearchItem.User.toChannel(): Channel =
        Channel(
            id = mid.toString(),
            name = name,
            thumbnailUrl = avatarUrl,
            subscriberCount = followerCount,
            description = description,
            videoCount = videoCount,
            url = "https://space.bilibili.com/$mid",
            serviceId = BILIBILI_SERVICE_ID,
        )
}
