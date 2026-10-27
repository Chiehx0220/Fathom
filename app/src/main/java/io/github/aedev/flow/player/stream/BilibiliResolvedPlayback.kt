package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BilibiliLivePlayback
import io.github.aedev.flow.bilibili.BilibiliPlayback
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.Video

/** Steps resolved by the Bilibili native client. */
sealed interface BilibiliStep : ResolvedPlayback

/** Bilibili VOD: no live/SABR/upcoming; one step = metadata + streams ([playback]) + related lane. */
data class VodFromBilibili(
    val playback: BilibiliPlayback,
    val relatedVideos: List<Video>,
    val preferredQuality: VideoQuality,
    val preferredAudioLanguage: String,
    val preferredCodecKey: String,
    val resumePositionOverrideMs: Long?,
) : BilibiliStep

/** Bilibili live room via native client: one step (no premiere/SABR variants). */
data class LiveFromBilibili(
    val playback: BilibiliLivePlayback,
) : BilibiliStep
