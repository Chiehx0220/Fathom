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
 * Bilibili's leg of [PlaybackLoadResolver]: what [InnerTubeVideoStreamExtractor] is for YouTube.
 * Fetches a video's streams and its related lane (or a live room's stream) through the native
 * client, and hands them back as one [ResolvedPlayback] step.
 */
internal class BilibiliPlaybackSource(
    private val api: BilibiliApi,
) {
    suspend fun resolve(
        request: PlaybackResolutionRequest,
        preferences: StreamPreferences,
    ): ResolvedPlayback.VodFromBilibili =
        coroutineScope {
            val (bvid, page) = BilibiliVideoId.parse(request.videoId)
            // Started first so the two requests overlap; a failure here only costs the related lane.
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
            ResolvedPlayback.VodFromBilibili(
                playback = playback,
                relatedVideos = related.await(),
                preferredQuality = preferences.quality,
                preferredAudioLanguage = preferences.audioLanguage,
                preferredCodecKey = preferences.codecKey,
                resumePositionOverrideMs = request.resumePositionOverrideMs,
            )
        }

    /** A live room, which has no related lane or parts: what plays is the room's own stream. */
    suspend fun resolveLive(request: PlaybackResolutionRequest): ResolvedPlayback.LiveFromBilibili {
        val roomId = BilibiliLiveId.roomIdOf(request.videoId) ?: throw BilibiliContentNotAvailableException("Not a live room id")
        return ResolvedPlayback.LiveFromBilibili(api.livePlayback(roomId))
    }

    companion object {
        private const val TAG = "BilibiliPlaybackSource"
        private const val RELATED_TIMEOUT_MS = 6_000L

        /** The related lane as screen [Video]s, without the video itself and without repeats. */
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

/** A Bilibili step's streams in the player's types, and the pair its preferences select. */
internal class BilibiliStreams(
    val videoStreams: List<VideoStream>,
    val audioStreams: List<AudioStream>,
    val selectedVideo: VideoStream?,
    val selectedAudio: AudioStream?,
)

/** Shared by the player screen and the gapless preload, so both pick the same streams for the same preferences. */
internal fun ResolvedPlayback.VodFromBilibili.buildStreams(): BilibiliStreams {
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
