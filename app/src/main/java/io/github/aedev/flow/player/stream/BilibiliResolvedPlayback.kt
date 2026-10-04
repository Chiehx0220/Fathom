package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BilibiliLivePlayback
import io.github.aedev.flow.bilibili.BilibiliPlayback
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.Video

/**
 * A VOD from Bilibili's native client. Bilibili has no live/SABR/upcoming variants here, so one
 * step carries everything: the metadata and streams in [playback], and the related lane.
 */
data class VodFromBilibili(
    val playback: BilibiliPlayback,
    val relatedVideos: List<Video>,
    val preferredQuality: VideoQuality,
    val preferredAudioLanguage: String,
    val preferredCodecKey: String,
    val resumePositionOverrideMs: Long?,
) : ResolvedPlayback

/** A live room from Bilibili's native client: one step, since it has no premiere or SABR variants. */
data class LiveFromBilibili(
    val playback: BilibiliLivePlayback,
) : ResolvedPlayback
