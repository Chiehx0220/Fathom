package io.github.aedev.flow.player.stream

import android.content.Context
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.NetworkState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Bilibili's leg of the gapless next-video preload: resolves a video into the same
 * [ResolvedStreamData] the YouTube leg produces, so the preload controller can append it as a second
 * player window without knowing which service it came from.
 */
internal object BilibiliPreloadResolver {
    private const val RESOLVE_TIMEOUT_MS = 25_000L
    private const val RELATED_TIMEOUT_MS = 6_000L

    suspend fun resolve(
        video: Video,
        context: Context,
        api: BilibiliApi,
    ): ResolvedStreamData? {
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
        // The related lane only feeds the autoplay list after promotion; failing to fetch it must not
        // cost the preload.
        val related =
            runCatching {
                withTimeoutOrNull(RELATED_TIMEOUT_MS) { BilibiliPlaybackSource.relatedVideos(api, bvid) }
            }.getOrNull().orEmpty()

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
