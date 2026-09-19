package io.github.aedev.flow.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliChannelPageKey
import io.github.aedev.flow.bilibili.BilibiliChannelVideo
import io.github.aedev.flow.data.model.DistinctKeyTracker
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A Bilibili uploader's Videos tab through the native client; the key is the 1-based page. */
class BilibiliChannelVideosPagingSource(
    private val api: BilibiliApi,
    private val mid: Long,
    private val channelName: String,
    private val channelAvatarUrl: String,
) : PagingSource<BilibiliChannelPageKey, Video>() {
    private val loadedKeys = DistinctKeyTracker()

    override fun getRefreshKey(state: PagingState<BilibiliChannelPageKey, Video>): BilibiliChannelPageKey? = null

    override suspend fun load(params: LoadParams<BilibiliChannelPageKey>): LoadResult<BilibiliChannelPageKey, Video> {
        val key = params.key ?: BilibiliChannelPageKey(page = 1, lastAid = 0L)
        return try {
            val result = withContext(Dispatchers.IO) { api.channelVideos(mid, key) }
            LoadResult.Page(
                data = loadedKeys.filter(result.videos.map { it.toVideo() }, Video::id),
                prevKey = null,
                nextKey = if (result.hasMore) BilibiliChannelPageKey(key.page + 1, result.lastAid) else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Channel videos failed for $mid page ${key.page}: ${e.message}")
            LoadResult.Error(e)
        }
    }

    private fun BilibiliChannelVideo.toVideo(): Video =
        Video(
            id = "$bvid?p=1",
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = authorName.ifBlank { channelName },
            channelId = mid.toString(),
            channelThumbnailUrl = channelAvatarUrl,
            viewCount = viewCount,
            duration = durationSec,
            uploadDate = uploadTimeSec.takeIf { it > 0 }?.let { DATE.format(Instant.ofEpochSecond(it)) } ?: "",
            timestamp = uploadTimeSec * 1000,
            serviceId = ServiceList.BiliBili.serviceId,
        )

    private companion object {
        const val TAG = "BilibiliChannelVideos"
        val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)
    }
}
