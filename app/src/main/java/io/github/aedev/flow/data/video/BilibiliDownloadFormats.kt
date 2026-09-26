package io.github.aedev.flow.data.video

import io.github.aedev.flow.bilibili.BilibiliPlayback
import io.github.aedev.flow.bilibili.BilibiliStreamFormat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.models.response.PlayerResponse

private const val VIDEO_ITAG_BASE = 100_000
private const val AUDIO_ITAG_BASE = 200_000
private const val AUDIO_SAMPLE_RATE = 44_100
private const val AUDIO_CHANNELS = 2

internal typealias DownloadFormats = List<PlayerResponse.StreamingData.Format>

/**
 * Bilibili's tracks in the shape the download dialogs already read, so a Bilibili video is offered the same quality,
 * codec and style choices as a YouTube one and downloads through the same pipeline. Bilibili's quality ids are not
 * unique per track, so each track gets its own number.
 */
internal fun BilibiliPlayback.toDownloadFormats(): Pair<DownloadFormats, DownloadFormats> {
    val durationMs = (info.durationSec * 1_000L).toString()
    val video = videoFormats.mapIndexed { index, format -> format.toFormat(VIDEO_ITAG_BASE + index, false, durationMs) }
    val audio = audioFormats.mapIndexed { index, format -> format.toFormat(AUDIO_ITAG_BASE + index, isAudio = true, durationMs) }
    return video to audio
}

/** The video with whatever the card left blank filled in from what Bilibili reports. */
internal fun Video.filledFrom(playback: BilibiliPlayback): Video =
    copy(
        title = title.ifBlank { playback.info.title },
        channelName = channelName.ifBlank { playback.info.uploader.name },
        channelId = channelId.ifBlank { playback.info.uploader.mid.toString() },
        thumbnailUrl = thumbnailUrl.ifBlank { playback.info.thumbnailUrl },
        duration = if (duration > 0) duration else playback.info.durationSec,
    )

private fun BilibiliStreamFormat.toFormat(
    itag: Int,
    isAudio: Boolean,
    durationMs: String,
) = PlayerResponse.StreamingData.Format(
    itag = itag,
    url = url,
    mimeType = "${if (isAudio) "audio" else "video"}/mp4; codecs=\"$codecs\"",
    bitrate = bandwidth,
    width = if (isAudio) null else width,
    height = if (isAudio) null else height,
    contentLength = null,
    quality = if (isAudio) "medium" else "hd$height",
    fps = if (isAudio) null else frameRate?.toDoubleOrNull()?.toInt(),
    qualityLabel = if (isAudio) null else "${height}p",
    averageBitrate = bandwidth,
    audioQuality = if (isAudio) "AUDIO_QUALITY_MEDIUM" else null,
    approxDurationMs = durationMs,
    audioSampleRate = if (isAudio) AUDIO_SAMPLE_RATE else null,
    audioChannels = if (isAudio) AUDIO_CHANNELS else null,
    loudnessDb = null,
    lastModified = null,
    signatureCipher = null,
)
