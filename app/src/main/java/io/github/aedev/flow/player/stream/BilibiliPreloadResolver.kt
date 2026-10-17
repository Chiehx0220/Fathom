package io.github.aedev.flow.player.stream

import android.content.Context
import android.util.Log
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliLiveId
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.resolvedServiceId
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.utils.NetworkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.StreamType

/** Next Bilibili video for gapless preload, same data as YouTube side. */
internal object BilibiliPreloadResolver {
    private const val TAG = "BilibiliPreload"
    private const val RESOLVE_TIMEOUT_MS = 25_000L

    /** Null: [video] not Bilibili, live room (never preloaded) or unresolved. */
    suspend fun resolveOrNull(
        video: Video,
        context: Context,
    ): ResolvedStreamData? {
        if (video.resolvedServiceId != BILIBILI_SERVICE_ID || BilibiliLiveId.isLive(video.id)) return null
        return try {
            resolve(video, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve ${video.id}: ${e.message}")
            null
        }
    }

    private suspend fun resolve(
        video: Video,
        context: Context,
    ): ResolvedStreamData? {
        val prefs = PlayerPreferences(context)
        val isWifi = NetworkState.isOnWifi(context)
        val preferences =
            StreamPreferences(
                quality = if (isWifi) prefs.defaultQualityWifi.first() else prefs.defaultQualityCellular.first(),
                audioLanguage = prefs.preferredAudioLanguage.first(),
                codecKey = prefs.videoCodecPriority.first(),
                subtitleLanguage = CaptionTrackResolver.NO_PREFERRED_LANGUAGE,
            )
        val request =
            PlaybackResolutionRequest(
                videoId = video.id,
                isWifi = isWifi,
                escalateToSabr = false,
                resumePositionOverrideMs = null,
                allowShorts = false,
            )
        // Same source as player screen: preloaded = what playback would build.
        val step =
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { BilibiliPlaybackSource(bilibiliApi(context)).resolve(request, preferences) }
                ?: return null
        val streams = step.buildStreams()

        return ResolvedStreamData(
            enrichedVideo = BilibiliVideoMapper.videoFromInfo(video.id, step.playback.info, fallback = video),
            videoStream = streams.selectedVideo,
            audioStream = streams.selectedAudio,
            videoStreams = streams.videoStreams,
            audioStreams = streams.audioStreams,
            subtitles = emptyList(),
            durationSeconds =
                step.playback.info.durationSec
                    .toLong(),
            dashManifestUrl = null,
            streamType = StreamType.VIDEO_STREAM,
            relatedVideos = step.relatedVideos,
            preferredCodec = preferences.codecKey,
            itVideoFormats = emptyList(),
            itAudioFormats = emptyList(),
            bilibiliInfo = step.playback.info,
        )
    }
}
