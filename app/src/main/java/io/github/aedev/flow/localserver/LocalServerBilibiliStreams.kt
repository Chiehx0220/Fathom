package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.content.DateWrapper
import io.github.aedev.flow.content.Description
import io.github.aedev.flow.content.Image
import io.github.aedev.flow.content.StreamInfo
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.player.stream.BilibiliStreamBridge
import io.github.aedev.flow.player.stream.model.StreamType
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Local server Bilibili video pages: metadata + streams as [StreamInfo]. Live rooms: [LocalServerBilibiliLive]. */
internal object LocalServerBilibiliStreams {
    private const val INFO_TTL_MS = 30 * 60 * 1000L

    private class CachedInfo(
        val info: StreamInfo,
        val expiresAtMs: Long,
    )

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
        infoCache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.info }

        val api = bilibiliApi(context)
        val playback = runBlocking { api.playback(bvid, part) }
        val related = runCatching { runBlocking { api.related(bvid) } }.getOrDefault(emptyList())
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
        infoCache[key] = CachedInfo(info, System.currentTimeMillis() + INFO_TTL_MS)
        infoCache.entries.removeIf { it.value.expiresAtMs < System.currentTimeMillis() }
        return info
    }
}
