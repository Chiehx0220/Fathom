package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bilibili's leg of [PlaybackLoadResolver]: what [InnerTubeVideoStreamExtractor] is for YouTube.
 * Fetches the video's streams through the native client and its related lane through the
 * repository, and hands both back as one [ResolvedPlayback] step.
 */
internal class BilibiliPlaybackSource(
    private val api: BilibiliApi,
    private val repository: YouTubeRepository,
) {
    suspend fun resolve(
        request: PlaybackResolutionRequest,
        preferences: StreamPreferences,
    ): ResolvedPlayback.VodFromBilibili =
        coroutineScope {
            val (bvid, page) = parseVideoId(request.videoId)
            // Started first so the two requests overlap; a failure here only costs the related lane.
            val related =
                async {
                    try {
                        withTimeoutOrNull(RELATED_TIMEOUT_MS) {
                            repository.getRelatedCandidates(request.videoId, request.serviceId)
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

    companion object {
        private const val TAG = "BilibiliPlaybackSource"
        private const val RELATED_TIMEOUT_MS = 6_000L

        /** "BV1xx?p=3" -> ("BV1xx", 3). A missing or unreadable part number means the first part. */
        internal fun parseVideoId(videoId: String): Pair<String, Int> {
            val bvid = videoId.substringBefore('?')
            val page =
                videoId
                    .substringAfter('?', "")
                    .split('&')
                    .firstOrNull { it.startsWith("p=") }
                    ?.removePrefix("p=")
                    ?.toIntOrNull()
                    ?.takeIf { it >= 1 }
                    ?: 1
            return bvid to page
        }
    }
}
