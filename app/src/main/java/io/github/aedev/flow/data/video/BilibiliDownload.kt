package io.github.aedev.flow.data.video

import android.content.Context
import io.github.aedev.flow.bilibili.BilibiliStreamFormat
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.downloader.FlowDownloadService
import io.github.aedev.flow.di.bilibiliApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Starts a download of a Bilibili video from the native client's own streams. */
object BilibiliDownload {
    /** One video track and, when the video has sound, one audio track, to be joined after download. */
    data class Choice(
        val video: BilibiliStreamFormat,
        val audio: BilibiliStreamFormat?,
    )

    /**
     * MP4-compatible tracks: AVC video, which the muxer joins with AAC audio without re-encoding.
     * Newer codecs are only used when the video has no AVC track.
     */
    fun choose(
        videos: List<BilibiliStreamFormat>,
        audios: List<BilibiliStreamFormat>,
        targetHeight: Int,
    ): Choice? {
        val pool = videos.filter { it.codecs.startsWith("avc1") }.ifEmpty { videos }
        if (pool.isEmpty()) return null
        val heights = pool.map { it.height }.distinct()
        val height =
            if (targetHeight == 0) {
                heights.max()
            } else {
                heights.filter { it <= targetHeight }.maxOrNull() ?: heights.min()
            }
        val video = pool.filter { it.height == height }.maxBy { it.bandwidth }
        val audio =
            audios.filter { it.codecs.startsWith("mp4a") }.maxByOrNull { it.bandwidth }
                ?: audios.maxByOrNull { it.bandwidth }
        return Choice(video, audio)
    }

    /** The heights on offer for [video], tallest first. */
    suspend fun availableHeights(
        context: Context,
        video: Video,
    ): List<Int> {
        val (bvid, page) = BilibiliVideoId.parse(video.id)
        val playback = withContext(Dispatchers.IO) { bilibiliApi(context).playback(bvid, page) }
        val pool = playback.videoFormats.filter { it.codecs.startsWith("avc1") }.ifEmpty { playback.videoFormats }
        return pool.map { it.height }.distinct().sortedDescending()
    }

    /** [targetHeight] 0 means the best available. Throws when the video cannot be resolved or has no video track. */
    suspend fun start(
        context: Context,
        video: Video,
        targetHeight: Int,
    ) {
        val (bvid, page) = BilibiliVideoId.parse(video.id)
        val playback = withContext(Dispatchers.IO) { bilibiliApi(context).playback(bvid, page) }
        val choice =
            choose(playback.videoFormats, playback.audioFormats, targetHeight)
                ?: throw IllegalStateException("No downloadable video stream")
        FlowDownloadService.startDownload(
            context = context,
            video =
                video.copy(
                    title = video.title.ifBlank { playback.info.title },
                    channelName = video.channelName.ifBlank { playback.info.uploader.name },
                    channelId = video.channelId.ifBlank { playback.info.uploader.mid.toString() },
                    thumbnailUrl = video.thumbnailUrl.ifBlank { playback.info.thumbnailUrl },
                    duration = if (video.duration > 0) video.duration else playback.info.durationSec,
                ),
            url = choice.video.url,
            quality = "${choice.video.height}p",
            audioUrl = choice.audio?.url,
        )
    }
}
