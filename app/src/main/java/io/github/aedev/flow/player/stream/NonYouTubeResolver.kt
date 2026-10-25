package io.github.aedev.flow.player.stream

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.di.bilibiliApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/**
 * Non-YouTube leg of [PlaybackLoadResolver]: InnerTube is YouTube-only; other services via native client, own step type.
 *
 * Offline copy checked by caller: no offline fallback here.
 */
class NonYouTubeResolver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val bilibili by lazy { BilibiliPlaybackSource(bilibiliApi(context)) }

        internal suspend fun resolve(
            request: PlaybackResolutionRequest,
            preferences: StreamPreferences,
            isCurrent: () -> Boolean,
            onStep: suspend (ResolvedPlayback) -> Unit,
        ) {
            val videoId = request.videoId
            val step =
                try {
                    bilibili.resolveAny(request, preferences)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Native extraction failed for $videoId", e)
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent()) return
                    // No premiere lookup (YouTube call).
                    onStep(ResolvedPlayback.Failed(PlaybackFailure.EXTRACTION, e, relatedVideos = null))
                    return
                }

            currentCoroutineContext().ensureActive()
            if (!isCurrent()) return
            onStep(step)
        }

        private companion object {
            const val TAG = "NonYouTubeResolver"
        }
    }
