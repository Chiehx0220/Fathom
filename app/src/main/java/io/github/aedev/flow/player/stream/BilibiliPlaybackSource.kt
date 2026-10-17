package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliContentNotAvailableException
import io.github.aedev.flow.bilibili.BilibiliLiveId
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Bilibili leg of [PlaybackLoadResolver] (YouTube: [InnerTubeVideoStreamExtractor]). Streams + related lane (or live room stream) via native client
 * → one [ResolvedPlayback] step.
 */
internal class BilibiliPlaybackSource(
    private val api: BilibiliApi,
) {
    suspend fun resolve(
        request: PlaybackResolutionRequest,
        preferences: StreamPreferences,
    ): VodFromBilibili =
        coroutineScope {
            val (bvid, page) = BilibiliVideoId.parse(request.videoId)
            // Started first: requests overlap; failure costs only related lane.
            val related =
                async {
                    try {
                        withTimeoutOrNull(RELATED_TIMEOUT_MS) {
                            relatedVideos(api, bvid)
                        }.orEmpty()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Related videos failed for ${request.videoId}: ${e.message}")
                        emptyList<Video>()
                    }
                }
            val playback = api.playback(bvid, page)
            VodFromBilibili(
                playback = playback,
                relatedVideos = related.await(),
                preferredQuality = preferences.quality,
                preferredAudioLanguage = preferences.audioLanguage,
                preferredCodecKey = preferences.codecKey,
                resumePositionOverrideMs = request.resumePositionOverrideMs,
            )
        }

    /** Live room: no related lane, no parts; plays the room's stream. */
    suspend fun resolveLive(request: PlaybackResolutionRequest): LiveFromBilibili {
        val roomId = BilibiliLiveId.roomIdOf(request.videoId) ?: throw BilibiliContentNotAvailableException("Not a live room id")
        return LiveFromBilibili(api.livePlayback(roomId))
    }

    companion object {
        private const val TAG = "BilibiliPlaybackSource"
        private const val RELATED_TIMEOUT_MS = 6_000L

        /** Related lane as screen [Video]s, minus the video, deduplicated. */
        internal suspend fun relatedVideos(
            api: BilibiliApi,
            bvid: String,
        ): List<Video> =
            api
                .related(bvid)
                .filter { it.bvid != bvid }
                .map { BilibiliVideoMapper.videoFromRelated(it) }
                .distinctBy { it.id }
    }
}

/** Step streams in player types + preference-selected pair. */
internal class BilibiliStreams(
    val videoStreams: List<VideoStream>,
    val audioStreams: List<AudioStream>,
    val selectedVideo: VideoStream?,
    val selectedAudio: AudioStream?,
)

/** Shared by player screen + gapless preload: same preferences → same streams. */
internal fun VodFromBilibili.buildStreams(): BilibiliStreams {
    val bvid = playback.info.bvid
    val videoStreams = BilibiliStreamBridge.convertVideoFormats(bvid, playback.videoFormats)
    val audioStreams = BilibiliStreamBridge.convertAudioFormats(bvid, playback.audioFormats)
    val selected =
        ServicePlaybackStreamSelector.selectStreams(
            videoCandidates = videoStreams,
            audioCandidatesAll = audioStreams,
            preferredQuality = preferredQuality,
            preferredAudioLanguage = preferredAudioLanguage,
            preferredCodecKey = preferredCodecKey,
        )
    return BilibiliStreams(videoStreams, audioStreams, selected.first, selected.second)
}
