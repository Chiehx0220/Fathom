/*
 * Stream construction follows PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * services/bilibili/extractors/BillibiliStreamExtractor.java (buildVideoOnlyStreamsArray, getAudioStreams).
 * Audio filtering follows PipePipeClient's ListHelper.filterUnsupportedFormats.
 */
package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliStreamFormat
import io.github.aedev.flow.player.datasource.BilibiliMirrors
import io.github.aedev.flow.player.stream.model.AudioStream
import io.github.aedev.flow.player.stream.model.ItagInfo
import io.github.aedev.flow.player.stream.model.MediaFormat
import io.github.aedev.flow.player.stream.model.Stream
import io.github.aedev.flow.player.stream.model.VideoStream
import java.util.Locale

/** Bilibili counterpart of [InnerTubeStreamBridge]: native formats → player streams; init/index ranges kept for DASH. */
object BilibiliStreamBridge {
    private const val TAG = "BilibiliStreamBridge"

    fun convertVideoFormats(
        bvid: String,
        formats: List<BilibiliStreamFormat>,
    ): List<VideoStream> =
        formats.mapIndexedNotNull { index, format ->
            BilibiliMirrors.register(format.url, format.backupUrls)
            try {
                val label = resolutionLabel(format)
                val item = ItagInfo(NO_ITAG, ItagInfo.ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, label, parseFps(format.frameRate))
                item.codec = format.codecs
                item.bitrate = format.bandwidth
                item.width = format.width
                item.height = format.height
                item.applyRanges(format)
                VideoStream(
                    id = "bilibili-$bvid-video-${format.id}-$index",
                    content = format.url,
                    format = MediaFormat.MPEG_4,
                    itagItem = item,
                    resolution = label,
                    isVideoOnly = true,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to build VideoStream for qn=${format.id}: ${e.message}")
                null
            }
        }

    /** FLAC dropped (decode failure); Dolby (ec-3) dropped (no opt-in). API order kept; highest bitrate picked. */
    fun convertAudioFormats(
        bvid: String,
        formats: List<BilibiliStreamFormat>,
    ): List<AudioStream> =
        formats
            .filter { isSupportedAudio(it.codecs) }
            .mapIndexedNotNull { index, format ->
                BilibiliMirrors.register(format.url, format.backupUrls)
                try {
                    val item = ItagInfo(NO_ITAG, ItagInfo.ItagType.AUDIO, MediaFormat.M4A, null, 0)
                    item.codec = format.codecs
                    item.bitrate = format.bandwidth
                    item.applyRanges(format)
                    AudioStream(
                        id = "bilibili-$bvid-audio-${format.id}-$index",
                        content = format.url,
                        format = MediaFormat.M4A,
                        itagItem = item,
                        averageBitrate = format.bandwidth,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to build AudioStream for qn=${format.id}: ${e.message}")
                    null
                }
            }

    /** Bilibili quality ids repeat across codecs: unusable as itag; -1 = none. */
    private const val NO_ITAG = -1

    /** Live room FLV address as the one muxed stream (no HLS published). */
    fun liveFlvStream(url: String): VideoStream =
        VideoStream(
            id = "bilibili-live-flv",
            content = url,
            format = null,
            itagItem = null,
            resolution = "",
            isVideoOnly = false,
        )

    /** Stream built by this bridge. */
    fun isBilibili(stream: Stream): Boolean = stream.id.startsWith("bilibili-")

    private fun ItagInfo.applyRanges(format: BilibiliStreamFormat) {
        format.initRange?.let {
            initStart = it.first.toInt()
            initEnd = it.last.toInt()
        }
        format.indexRange?.let {
            indexStart = it.first.toInt()
            indexEnd = it.last.toInt()
        }
    }

    private fun isSupportedAudio(codecs: String): Boolean {
        val codec = codecs.lowercase(Locale.ROOT)
        return !codec.startsWith("flac") && !codec.startsWith("ec-3")
    }

    /**
     * "1080p"/"1080p60" label: read first by [VideoCodecUtils.qualityHeightFromStream]; Bilibili tier name (720x1280 portrait = "720p"),
     * not pixel height.
     */
    private fun resolutionLabel(format: BilibiliStreamFormat): String {
        val tier = tierHeight(format)
        val fps = parseFps(format.frameRate)
        return if (fps >= 50) "${tier}p$fps" else "${tier}p"
    }

    private fun tierHeight(format: BilibiliStreamFormat): Int =
        when (format.id) {
            127 -> 4320

            126, 125, 120 -> 2160

            116, 112, 80 -> 1080

            74, 64 -> 720

            32 -> 480

            16 -> 360

            6 -> 240

            // Unknown id: shorter side (tier for landscape and portrait).
            else -> minOf(format.width, format.height).takeIf { it > 0 } ?: format.height
        }

    private fun parseFps(frameRate: String?): Int {
        if (frameRate.isNullOrEmpty()) return 0
        return runCatching {
            if (frameRate.contains("/")) {
                val (num, den) = frameRate.split("/")
                (num.toDouble() / den.toDouble()).toInt()
            } else {
                frameRate.toDouble().toInt()
            }
        }.getOrDefault(0)
    }
}
