package io.github.aedev.flow.data.subscriptions

import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.data.innertube.ChannelLabel
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** Uploads of several Bilibili uploaders + per-uploader failure reasons. */
internal data class BilibiliFeedChunk(
    val videos: List<Video>,
    val failureReasons: Map<String, String>,
)

/** Bilibili half of subscription refresh: no RSS/channel tabs, latest uploads via native API. */
@Singleton
class BilibiliSubscriptionFeed
    @Inject
    constructor(
        private val bilibiliApi: BilibiliApi,
    ) {
        /** Latest uploads of one uploader; [channelId] numeric, [label] = name + avatar. */
        suspend fun latestVideos(
            channelId: String,
            limit: Int = 5,
            label: ChannelLabel? = null,
        ): List<Video> {
            val mid = channelId.toLongOrNull() ?: return emptyList()
            return bilibiliApi.latestVideos(mid, limit, channelName = label?.name.orEmpty(), channelAvatarUrl = label?.avatarUrl.orEmpty())
        }

        /** Reads [channelIds] in chunks, each chunk to [onChunk]. Uploads older than [minimumDateMillis] dropped. */
        internal suspend fun readInChunks(
            channelIds: List<String>,
            labelByChannel: Map<String, ChannelLabel>,
            minimumDateMillis: Long,
            onChunk: suspend (BilibiliFeedChunk) -> Unit,
        ) {
            for (chunk in channelIds.chunked(CHUNK_SIZE)) {
                val results =
                    coroutineScope {
                        chunk
                            .map { channelId ->
                                async(Dispatchers.IO) { channelId to read(channelId, minimumDateMillis, labelByChannel[channelId]) }
                            }.awaitAll()
                    }
                val failures = HashMap<String, String>()
                val videos = ArrayList<Video>()
                for ((channelId, result) in results) {
                    result.onSuccess { videos += it }.onFailure { failures[channelId] = "${it::class.simpleName}: ${it.message}" }
                }
                onChunk(BilibiliFeedChunk(videos, failures))
            }
        }

        private suspend fun read(
            channelId: String,
            minimumDateMillis: Long,
            label: ChannelLabel?,
        ): Result<List<Video>> =
            try {
                Result.success(latestVideos(channelId, MAX_VIDEOS_PER_CHANNEL, label).filter { it.timestamp > minimumDateMillis })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "[$channelId] Bilibili channel failed (${e::class.simpleName}): ${e.message}")
                Result.failure(e)
            }

        private companion object {
            const val TAG = "BilibiliSubscriptionFeed"

            /** Bilibili rate-limits bursts: chunked reads. */
            const val CHUNK_SIZE = 3
            const val MAX_VIDEOS_PER_CHANNEL = 60
        }
    }
