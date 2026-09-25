package io.github.aedev.flow.data.video

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.StreamSizeEstimator
import io.github.aedev.flow.player.stream.durationMs
import io.github.aedev.flow.player.stream.playableAudioFormats
import io.github.aedev.flow.player.stream.playableVideoFormats
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val EXTRACT_TIMEOUT_MS = 8_000L

/** Everything the download dialogs need to offer one video: its formats and each choice's size. */
data class VideoDownloadOptions(
    val video: Video,
    val videoFormats: List<PlayerResponse.StreamingData.Format>,
    val audioFormats: List<PlayerResponse.StreamingData.Format>,
    val streamSizes: Map<String, Long>,
)

/**
 * Loads the formats for a video that is not playing, so a card can open the same download dialog
 * the player and Shorts open, with the same quality, codec and style settings applied.
 */
class VideoDownloadOptionsLoader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun load(video: Video): VideoDownloadOptions? =
            withContext(PerformanceDispatcher.networkIO) {
                if (BilibiliVideoId.isBilibili(video.id)) return@withContext loadBilibili(video)
                val result =
                    withTimeoutOrNull(EXTRACT_TIMEOUT_MS) {
                        runCatching { InnerTubeVideoStreamExtractor.extract(video.id) }.getOrNull()
                    } ?: return@withContext null
                val videoFormats = result.playableVideoFormats()
                if (videoFormats.isEmpty()) return@withContext null
                val audioFormats = result.playableAudioFormats()
                VideoDownloadOptions(
                    video = video,
                    videoFormats = videoFormats,
                    audioFormats = audioFormats,
                    streamSizes = StreamSizeEstimator.fromInnerTubeFormats(videoFormats, audioFormats, result.durationMs() ?: 0L),
                )
            }

        private suspend fun loadBilibili(video: Video): VideoDownloadOptions? {
            val (bvid, page) = BilibiliVideoId.parse(video.id)
            val playback = withTimeoutOrNull(EXTRACT_TIMEOUT_MS) { runCatching { bilibiliApi(context).playback(bvid, page) }.getOrNull() } ?: return null
            val (videoFormats, audioFormats) = playback.toDownloadFormats()
            if (videoFormats.isEmpty()) return null
            return VideoDownloadOptions(
                video = video.filledFrom(playback),
                videoFormats = videoFormats,
                audioFormats = audioFormats,
                streamSizes = emptyMap(),
            )
        }
    }
