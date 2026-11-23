package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliPlayback
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.bilibili.BilibiliRelated
import io.github.aedev.flow.bilibili.BilibiliVideoInfo
import io.github.aedev.flow.content.DateWrapper
import io.github.aedev.flow.content.Description
import io.github.aedev.flow.content.Frameset
import io.github.aedev.flow.content.Image
import io.github.aedev.flow.content.StreamInfo
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.player.stream.BilibiliStreamBridge
import io.github.aedev.flow.player.stream.model.StreamType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Local server Bilibili video pages: metadata + streams as [StreamInfo]. Live rooms: [LocalServerBilibiliLive]. */
internal object LocalServerBilibiliStreams {
    private const val INFO_TTL_MS = 30 * 60 * 1000L
    private const val PREVIEW_RETRY_MS = 30 * 1000L
    private const val PREVIEW_GIVE_UP_MS = 10 * 60 * 1000L

    /** Bilibili answers previews empty at first: [retryPreviews] backfills. */
    private class CachedInfo(
        val info: StreamInfo,
        val video: BilibiliVideoInfo,
        val expiresAtMs: Long,
        val createdAtMs: Long,
    ) {
        @Volatile var nextPreviewTryMs = createdAtMs + PREVIEW_RETRY_MS
    }

    private val infoCache = ConcurrentHashMap<String, CachedInfo>()

    /**
     * Metadata, video-only + audio-only DASH, related list. Short-lived cache shared by watch page, manifest, stream requests.
     */
    fun streamInfo(
        context: Context,
        mediaUrl: String,
    ): StreamInfo {
        LocalServerBilibiliLive.roomIdOf(mediaUrl)?.let { return LocalServerBilibiliLive.streamInfo(context, it) }
        val id = LocalServerBilibili.videoIdOf(mediaUrl)
        val ref = BilibiliRef.Video.parse(id)
        val (bvid, part) = ref.bvid to ref.part
        val key = BilibiliRef.Video.of(bvid, part).id
        infoCache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let {
            retryPreviews(context, it)
            return it.info
        }

        val api = bilibiliApi(context)
        val playback = runBlocking { api.playback(bvid, part) }
        val video = playback.info
        val (related, shot) =
            runBlocking {
                coroutineScope {
                    val relatedItems = async { runCatching { api.related(bvid) }.getOrDefault(emptyList()) }
                    val previews = async { fetchPreviews(api, video) }
                    relatedItems.await() to previews.await()
                }
            }

        val info = buildInfo(key, playback, related, shot)
        val now = System.currentTimeMillis()
        infoCache[key] = CachedInfo(info, video, now + INFO_TTL_MS, now)
        infoCache.entries.removeIf { it.value.expiresAtMs < now }
        return info
    }

    /** Previews still missing, retry spacing passed, give-up window not over. */
    internal fun previewRetryDue(
        hasFrames: Boolean,
        nowMs: Long,
        nextTryMs: Long,
        createdAtMs: Long,
    ) = !hasFrames && nowMs >= nextTryMs && nowMs <= createdAtMs + PREVIEW_GIVE_UP_MS

    private fun buildInfo(
        key: String,
        playback: BilibiliPlayback,
        related: List<BilibiliRelated>,
        previews: Frameset?,
    ): StreamInfo {
        val video = playback.info
        val info =
            StreamInfo(
                LocalServerBilibili.serviceId,
                LocalServerBilibili.videoUrl(key),
                LocalServerBilibili.videoUrl(key),
                StreamType.VIDEO_STREAM,
                key,
                video.title,
            )
        info.thumbnails = listOf(Image(video.thumbnailUrl))
        info.description = Description(video.description, Description.PLAIN_TEXT)
        info.duration = video.durationSec.toLong()
        info.viewCount = video.viewCount
        info.likeCount = video.likeCount
        info.uploadDate = DateWrapper(OffsetDateTime.ofInstant(Instant.ofEpochSecond(video.uploadTimeSec), ZoneOffset.ofHours(8)))
        info.uploaderName = video.uploader.name
        info.uploaderUrl = LocalServerBilibili.channelUrl(video.uploader.mid.toString())
        info.uploaderAvatars = listOf(Image(video.uploader.avatarUrl))
        info.videoOnlyStreams = BilibiliStreamBridge.convertVideoFormats(video.bvid, playback.videoFormats)
        info.audioStreams = BilibiliStreamBridge.convertAudioFormats(video.bvid, playback.audioFormats)
        info.previewFrames = listOfNotNull(previews)
        info.relatedItems =
            related.map {
                bilibiliVideoItem(
                    "${it.bvid}?p=1",
                    it.title,
                    it.thumbnailUrl,
                    it.durationSec,
                    it.viewCount,
                    it.uploader.name,
                    it.uploader.mid,
                    it.uploader.avatarUrl,
                )
            }
        return info
    }

    private suspend fun fetchPreviews(
        api: BilibiliApi,
        video: BilibiliVideoInfo,
    ): Frameset? =
        runCatching { api.videoshot(video) }
            .onFailure { serverLog("Bilibili previews for ${video.bvid} failed: ${it.message}") }
            .getOrNull()

    private fun retryPreviews(
        context: Context,
        cached: CachedInfo,
    ) {
        val now = System.currentTimeMillis()
        if (!previewRetryDue(cached.info.previewFrames.isNotEmpty(), now, cached.nextPreviewTryMs, cached.createdAtMs)) return
        cached.nextPreviewTryMs = now + PREVIEW_RETRY_MS
        runBlocking { fetchPreviews(bilibiliApi(context), cached.video) }?.let { cached.info.previewFrames = listOf(it) }
    }
}
