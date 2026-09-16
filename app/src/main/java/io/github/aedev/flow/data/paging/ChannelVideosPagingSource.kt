package io.github.aedev.flow.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.aedev.flow.data.model.DistinctKeyTracker
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.isYouTubeServiceId
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

/**
 * PagingSource for a non-YouTube channel's Videos tab (e.g. Bilibili), through the generic
 * extractor's [ChannelTabInfo] pagination. YouTube's own Videos tab is fetched through Flow's
 * native InnerTube client instead (see ChannelTabController) - this exists for every other
 * service, mirroring [ChannelPlaylistsPagingSource].
 */
class ChannelVideosPagingSource(
    private val videosTab: ListLinkHandler?,
    private val channelInfo: ChannelInfo,
) : PagingSource<Page, Video>() {
    companion object {
        private const val TAG = "ChannelVideosPaging"
    }

    private val loadedVideoKeys = DistinctKeyTracker()

    override fun getRefreshKey(state: PagingState<Page, Video>): Page? = null

    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, Video> =
        try {
            withContext(Dispatchers.IO) {
                if (videosTab == null) {
                    return@withContext LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
                }

                val page = params.key
                val service = NewPipe.getService(channelInfo.serviceId)
                val (items, nextPage) =
                    if (page == null) {
                        val tabInfo = ChannelTabInfo.getInfo(service, videosTab)
                        tabInfo.relatedItems to tabInfo.nextPage
                    } else {
                        val moreItems = ChannelTabInfo.getMoreItems(service, videosTab, page)
                        moreItems.items to moreItems.nextPage
                    }

                val videos =
                    items
                        .filterIsInstance<StreamInfoItem>()
                        .map { it.toChannelVideo(channelInfo) }

                LoadResult.Page(
                    data = loadedVideoKeys.filter(videos, Video::id),
                    prevKey = null,
                    nextKey = nextPage,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading channel videos", e)
            LoadResult.Error(e)
        }
}

internal fun StreamInfoItem.toChannelVideo(channelInfo: ChannelInfo): Video {
    val isYouTube = channelInfo.serviceId.isYouTubeServiceId
    val videoId =
        if (!isYouTube) {
            // Other services' ids (e.g. Bilibili's "BVxxxxxxxxxx?p=1") don't fit the YouTube-shaped
            // patterns below - resolve through the service's own link handler instead of guessing
            // at URL structure.
            runCatching {
                NewPipe.getService(channelInfo.serviceId).streamLHFactory.getId(url)
            }.getOrDefault(url.substringAfterLast("/").substringBefore("?"))
        } else {
            when {
                url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
                url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
                else -> url.substringAfterLast("/").substringBefore("?")
            }
        }
    val thumbnail = ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, thumbnailUrl)
    val absoluteUploadTimestamp = uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
    val textualDate = textualUploadDate?.takeIf { it.isNotBlank() }
    val displayUploadDate =
        textualDate
            ?: io.github.aedev.flow.utils
                .formatTimeAgo(uploadDate?.offsetDateTime()?.toString())
    val uploadTimestamp =
        absoluteUploadTimestamp
            ?: parseRelativeUploadDate(textualDate)
            ?: 0L
    return Video(
        id = videoId,
        title = name,
        thumbnailUrl = thumbnail,
        channelName = uploaderName ?: channelInfo.name,
        channelId = channelInfo.id,
        channelThumbnailUrl =
            channelInfo.avatars.maxByOrNull { it.height }?.url
                ?: channelInfo.avatars.firstOrNull()?.url
                ?: "",
        viewCount = viewCount,
        duration = duration.toInt().coerceAtLeast(0),
        uploadDate = displayUploadDate,
        timestamp = uploadTimestamp,
        description = "",
        serviceId = channelInfo.serviceId,
    )
}

private fun parseRelativeUploadDate(text: String?): Long? {
    val normalized =
        text
            ?.lowercase(Locale.US)
            ?.replace("streamed", "")
            ?.replace("premiered", "")
            ?.replace("live", "")
            ?.replace("ago", "")
            ?.trim()
            ?: return null

    if (normalized.isBlank()) return null
    if (normalized.contains("just now") || normalized.contains("today")) return System.currentTimeMillis()
    if (normalized.contains("yesterday")) return System.currentTimeMillis() - 24L * 60L * 60L * 1000L

    val value =
        Regex("(\\d+)")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return null
    val unitMillis =
        when {
            normalized.contains("second") || normalized.endsWith("s") -> 1_000L
            normalized.contains("minute") || normalized.endsWith("m") -> 60_000L
            normalized.contains("hour") || normalized.endsWith("h") -> 3_600_000L
            normalized.contains("day") || normalized.endsWith("d") -> 86_400_000L
            normalized.contains("week") || normalized.endsWith("w") -> 7L * 86_400_000L
            normalized.contains("month") || normalized.endsWith("mo") -> 30L * 86_400_000L
            normalized.contains("year") || normalized.endsWith("y") -> 365L * 86_400_000L
            else -> return null
        }

    return System.currentTimeMillis() - (value * unitMillis)
}
