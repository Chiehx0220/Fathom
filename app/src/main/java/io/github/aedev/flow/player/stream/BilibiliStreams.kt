package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BilibiliRef
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

/** Streams in player types + preferred pair. */
internal class BilibiliStreams(
    val videoStreams: List<VideoStream>,
    val audioStreams: List<AudioStream>,
    val selectedVideo: VideoStream?,
    val selectedAudio: AudioStream?,
)

/** Shared by player screen, gapless preload. */
internal fun VodFromBilibili.buildStreams(): BilibiliStreams {
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
