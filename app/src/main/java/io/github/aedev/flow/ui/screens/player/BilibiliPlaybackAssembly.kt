package io.github.aedev.flow.ui.screens.player

import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.BilibiliStreamBridge
import io.github.aedev.flow.player.stream.BilibiliVideoMapper
import io.github.aedev.flow.player.stream.LiveFromBilibili
import io.github.aedev.flow.player.stream.VideoQualityOptions
import io.github.aedev.flow.player.stream.VodFromBilibili
import io.github.aedev.flow.player.stream.buildStreams

// Bilibili halves of PlaybackStreamPreparer, PlaybackPreparer; extensions keep upstream files Bilibili-free.

private fun identityOf(video: Video) =
    PlaybackStreamPreparer.StreamIdentity(
        enrichedVideo = video,
        title = video.title,
        channel = video.channelName,
        thumbnail = video.thumbnailUrl,
        channelId = video.channelId,
        embeddedAvatarUrls = listOfNotNull(video.channelThumbnailUrl.takeIf { it.isNotBlank() }),
    )

/** Bilibili counterpart: streams + identity without a watch response. */
internal fun PlaybackStreamPreparer.assembleVod(
    videoId: String,
    cached: Video?,
    step: VodFromBilibili,
): PlaybackStreamPreparer.VodStreams {
    val info = step.playback.info
    val streams = step.buildStreams()
    val videoStreams = streams.videoStreams
    val audioStreams = streams.audioStreams
    return PlaybackStreamPreparer.VodStreams(
        identity = identityOf(BilibiliVideoMapper.videoFromInfo(videoId, info, cached)),
        durationSeconds = info.durationSec.toLong(),
        videoStreams = videoStreams,
        audioStreams = audioStreams,
        availableQualities = VideoQualityOptions.availableQualities(videoStreams),
        videoStream = streams.selectedVideo,
        audioStream = streams.selectedAudio,
        subtitles = emptyList(),
        isAdaptiveMode = step.preferredQuality == VideoQuality.AUTO,
        streamSizes = emptyMap(),
    )
}

/** Bilibili counterpart: room HLS master, or FLV if no HLS. */
internal fun PlaybackStreamPreparer.assembleLive(
    videoId: String,
    cached: Video?,
    step: LiveFromBilibili,
): PlaybackStreamPreparer.LiveStreams {
    val video = BilibiliVideoMapper.videoFromLive(videoId, step.playback.room, cached)
    val streams = step.playback.streams
    return PlaybackStreamPreparer.LiveStreams(
        identity = identityOf(video),
        hlsUrl = streams.hlsMasterUrl,
        dashManifestUrl = null,
        subtitles = emptyList(),
        progressiveStream = streams.flvUrl.takeIf { streams.hlsMasterUrl == null }?.let(BilibiliStreamBridge::liveFlvStream),
    )
}

/** Bilibili VOD assembly, same hand-off; no SABR session, no InnerTube formats. */
internal suspend fun PlaybackPreparer.prepareBilibiliVodStreams(
    videoId: String,
    streams: PlaybackStreamPreparer.VodStreams,
    step: VodFromBilibili,
    savedPositionMs: Long,
    isCurrent: () -> Boolean,
) = prepareVodStreams(
    videoId = videoId,
    videoStream = streams.videoStream,
    audioStream = streams.audioStream,
    videoStreams = streams.videoStreams,
    audioStreams = streams.audioStreams,
    subtitles = streams.subtitles,
    durationSeconds = streams.durationSeconds,
    savedPositionMs = savedPositionMs,
    resumeOverrideRequested = step.resumePositionOverrideMs != null,
    isAdaptiveMode = streams.isAdaptiveMode,
    sabrInfo = null,
    itVideoFormats = emptyList(),
    itAudioFormats = emptyList(),
    preferredVideoCodec = step.preferredCodecKey,
    preferredLiveQualityHeight = step.preferredQuality.height,
    isCurrent = isCurrent,
)
