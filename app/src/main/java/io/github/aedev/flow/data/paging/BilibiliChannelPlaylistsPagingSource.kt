package io.github.aedev.flow.data.paging

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliPlaylistId
import io.github.aedev.flow.data.model.DistinctKeyTracker
import io.github.aedev.flow.data.model.Playlist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A Bilibili uploader's Playlists tab (series and seasons) through the native client; the key is the 1-based page. */
class BilibiliChannelPlaylistsPagingSource(
    private val api: BilibiliApi,
    private val mid: Long,
) : PagingSource<Int, Playlist>() {
    private val loadedKeys = DistinctKeyTracker()

    override fun getRefreshKey(state: PagingState<Int, Playlist>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Playlist> {
        val page = params.key ?: 1
        return try {
            val result = withContext(Dispatchers.IO) { api.channelPlaylists(mid, page) }
            val playlists =
                result.items.map { ref ->
                    Playlist(
                        id = BilibiliPlaylistId.encode(ref.kind, ref.mid, ref.id, ref.name),
                        name = ref.name,
                        thumbnailUrl = ref.coverUrl,
                        videoCount = ref.videoCount,
                        isLocal = false,
                        serviceId = BILIBILI_SERVICE_ID,
                    )
                }
            LoadResult.Page(
                data = loadedKeys.filter(playlists, Playlist::id),
                prevKey = null,
                nextKey = if (result.hasMore) page + 1 else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("BilibiliChannelPlaylists", "Channel playlists failed for $mid page $page: ${e.message}")
            LoadResult.Error(e)
        }
    }
}
