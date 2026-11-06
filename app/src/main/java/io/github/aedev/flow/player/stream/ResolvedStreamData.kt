package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BilibiliVideoInfo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Everything resolving one video's streams produced: enough to start playback, build a preloaded
 * media source, or fill in the player state, without going back to the network.
 *
 * [hlsUrl] and [sabrInfo]: direct playback only (queue/autoplay advance); gapless preload skips live and SABR.
 */
internal data class ResolvedStreamData(
    val enrichedVideo: Video,
    val videoStream: VideoStream?,
    val audioStream: AudioStream?,
    val videoStreams: List<VideoStream>,
    val audioStreams: List<AudioStream>,
    val subtitles: List<ResolvedCaption>,
    val durationSeconds: Long,
    val dashManifestUrl: String?,
    val streamType: StreamType?,
    val relatedVideos: List<Video>,
    val preferredCodec: String,
    val itVideoFormats: List<PlayerResponse.StreamingData.Format>,
    val itAudioFormats: List<PlayerResponse.StreamingData.Format>,
    val hlsUrl: String? = null,
    val sabrInfo: SabrStreamInfo? = null,
    /** Bilibili only: danmaku load key once preload becomes current. */
    val bilibiliInfo: BilibiliVideoInfo? = null,
)
