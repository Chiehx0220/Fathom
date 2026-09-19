package io.github.aedev.flow.player.stream

import android.content.Context
import android.util.Log
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.utils.NetworkState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamType

/** Resolves the next Bilibili video for the gapless preload, into the same data the YouTube side produces. */
internal object BilibiliPreloadResolver {
    private const val TAG = "BilibiliPreload"
    private const val RESOLVE_TIMEOUT_MS = 25_000L
    private const val RELATED_TIMEOUT_MS = 6_000L

    /** Null when [video] is not Bilibili's or could not be resolved; the preload then retries later. */
    suspend fun resolveOrNull(
        video: Video,
        context: Context,
    ): ResolvedStreamData? {
        if (video.serviceId != ServiceList.BiliBili.serviceId) return null
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
        val api = bilibiliApi(context)
        val (bvid, page) = BilibiliPlaybackSource.parseVideoId(video.id)
        val playback = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { api.playback(bvid, page) } ?: return null

        val prefs = PlayerPreferences(context)
        val preferredQuality =
            if (NetworkState.isOnWifi(context)) prefs.defaultQualityWifi.first() else prefs.defaultQualityCellular.first()
        val preferredCodecKey = prefs.videoCodecPriority.first()

        val videoStreams = BilibiliStreamBridge.convertVideoFormats(playback.info.bvid, playback.videoFormats)
        val audioStreams = BilibiliStreamBridge.convertAudioFormats(playback.info.bvid, playback.audioFormats)
        val selected =
            ServicePlaybackStreamSelector.selectStreams(
                videoCandidates = videoStreams,
                audioCandidatesAll = audioStreams,
                preferredQuality = preferredQuality,
                preferredAudioLanguage = prefs.preferredAudioLanguage.first(),
                preferredCodecKey = preferredCodecKey,
            )
        // A missing related list must not cost the preload.
        val related =
            try {
                withTimeoutOrNull(RELATED_TIMEOUT_MS) { BilibiliPlaybackSource.relatedVideos(api, bvid) }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }

        return ResolvedStreamData(
            enrichedVideo = BilibiliVideoMapper.videoFromInfo(video.id, playback.info, fallback = video),
            videoStream = selected.first,
            audioStream = selected.second,
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            subtitles = emptyList(),
            durationSeconds = playback.info.durationSec.toLong(),
            dashManifestUrl = null,
            streamType = StreamType.VIDEO_STREAM,
            relatedVideos = related,
            preferredCodec = preferredCodecKey,
            itVideoFormats = emptyList(),
            itAudioFormats = emptyList(),
            bilibiliInfo = playback.info,
        )
    }
}
