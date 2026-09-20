package org.schabi.newpipe.localserver

import android.content.Context
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.player.stream.BilibiliStreamBridge
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** The local server's Bilibili video pages: one video's metadata and streams, as a [StreamInfo]. */
internal object LocalServerBilibiliStreams {
    private const val INFO_TTL_MS = 30 * 60 * 1000L

    private class CachedInfo(
        val info: StreamInfo,
        val expiresAtMs: Long,
    )

    private val infoCache = ConcurrentHashMap<String, CachedInfo>()

    /**
     * One video's metadata, DASH streams and related list, in the shape the watch page reads. The
     * streams are video-only and audio-only, as Bilibili serves them. Cached for a while, because a
     * watch page, its manifest and every stream request each ask for the same video.
     */
    fun streamInfo(
        context: Context,
        mediaUrl: String,
    ): StreamInfo {
        val id = LocalServerBilibili.videoIdOf(mediaUrl)
        val (bvid, part) = BilibiliVideoId.parse(id)
        val key = "$bvid?p=$part"
        infoCache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.info }

        val api = bilibiliApi(context)
        val playback = runBlocking { api.playback(bvid, part) }
        val related = runCatching { runBlocking { api.related(bvid) } }.getOrDefault(emptyList())
        val video = playback.info

        val info = StreamInfo(LocalServerBilibili.serviceId, LocalServerBilibili.videoUrl(key), LocalServerBilibili.videoUrl(key), StreamType.VIDEO_STREAM, key, video.title, 0)
        info.thumbnails = listOf(Image(video.thumbnailUrl, -1, -1, Image.ResolutionLevel.UNKNOWN))
        info.description = Description(video.description, Description.PLAIN_TEXT)
        info.duration = video.durationSec.toLong()
        info.viewCount = video.viewCount
        info.likeCount = video.likeCount
        info.uploadDate = DateWrapper(OffsetDateTime.ofInstant(Instant.ofEpochSecond(video.uploadTimeSec), ZoneOffset.ofHours(8)))
        info.uploaderName = video.uploader.name
        info.uploaderUrl = LocalServerBilibili.channelUrl(video.uploader.mid.toString())
        info.uploaderAvatars = listOf(Image(video.uploader.avatarUrl, -1, -1, Image.ResolutionLevel.UNKNOWN))
        info.videoOnlyStreams = BilibiliStreamBridge.convertVideoFormats(video.bvid, playback.videoFormats)
        info.audioStreams = BilibiliStreamBridge.convertAudioFormats(video.bvid, playback.audioFormats)
        info.relatedItems =
            related.map {
                bilibiliVideoItem("${it.bvid}?p=1", it.title, it.thumbnailUrl, it.durationSec, it.viewCount, it.uploader.name, it.uploader.mid, it.uploader.avatarUrl)
            }
        infoCache[key] = CachedInfo(info, System.currentTimeMillis() + INFO_TTL_MS)
        infoCache.entries.removeIf { it.value.expiresAtMs < System.currentTimeMillis() }
        return info
    }
}
