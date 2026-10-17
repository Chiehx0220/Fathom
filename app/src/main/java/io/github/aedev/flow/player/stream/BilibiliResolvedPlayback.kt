package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BilibiliLivePlayback
import io.github.aedev.flow.bilibili.BilibiliPlayback
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.Video

/**
 * Bilibili VOD via native client. No live/SABR/upcoming variants: one step = metadata + streams ([playback]) + related lane.
 */
data class VodFromBilibili(
    val playback: BilibiliPlayback,
    val relatedVideos: List<Video>,
    val preferredQuality: VideoQuality,
    val preferredAudioLanguage: String,
    val preferredCodecKey: String,
    val resumePositionOverrideMs: Long?,
) : ResolvedPlayback

/** Bilibili live room via native client: one step (no premiere/SABR variants). */
data class LiveFromBilibili(
    val playback: BilibiliLivePlayback,
) : ResolvedPlayback
