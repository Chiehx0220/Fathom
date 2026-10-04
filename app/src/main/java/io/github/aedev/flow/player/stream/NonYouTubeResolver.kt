package io.github.aedev.flow.player.stream

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.bilibili.BilibiliLiveId
import io.github.aedev.flow.di.bilibiliApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/**
 * The non-YouTube leg of [PlaybackLoadResolver]: InnerTube is YouTube's own API, so every other
 * service resolves through its native client and produces its own step type.
 *
 * The caller has already checked for an offline copy, so there is no offline fallback here.
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
                    if (BilibiliLiveId.isLive(videoId)) {
                        bilibili.resolveLive(request)
                    } else {
                        bilibili.resolve(request, preferences)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Native extraction failed for $videoId (service ${request.serviceId})", e)
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent()) return
                    // No premiere lookup: that is a YouTube call and means nothing for these ids.
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
