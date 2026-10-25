package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliContentNotAvailableException
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bilibili leg of [PlaybackLoadResolver] (YouTube: [InnerTubeVideoStreamExtractor]). Streams + related lane (or live room stream) via native client
 * → one [ResolvedPlayback] step.
 */
internal class BilibiliPlaybackSource(
    private val api: BilibiliApi,
) {
    /** Room or video by id. */
    suspend fun resolveAny(
        request: PlaybackResolutionRequest,
        preferences: StreamPreferences,
    ): ResolvedPlayback = if (BilibiliRef.Live.ofId(request.videoId) != null) resolveLive(request) else resolve(request, preferences)

    suspend fun resolve(
        request: PlaybackResolutionRequest,
        preferences: StreamPreferences,
    ): VodFromBilibili =
        coroutineScope {
            val ref = BilibiliRef.Video.parse(request.videoId)
            // Started first: requests overlap; failure costs only related lane.
            val related =
                async {
                    try {
                        withTimeoutOrNull(RELATED_TIMEOUT_MS) {
                            relatedVideos(api, ref.bvid)
                        }.orEmpty()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Related videos failed for ${request.videoId}: ${e.message}")
                        emptyList<Video>()
                    }
                }
            val playback = api.playback(ref.bvid, ref.part)
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
        val roomId = BilibiliRef.Live.ofId(request.videoId)?.roomId ?: throw BilibiliContentNotAvailableException("Not a live room id")
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
