/*
 * Stream construction follows PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * services/bilibili/extractors/BillibiliStreamExtractor.java (buildVideoOnlyStreamsArray, getAudioStreams).
 * Audio filtering follows PipePipeClient's ListHelper.filterUnsupportedFormats.
 */
package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliStreamFormat
import io.github.aedev.flow.player.datasource.BilibiliMirrors
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

/**
 * The Bilibili counterpart of [InnerTubeStreamBridge]: turns the native client's formats into the
 * [VideoStream] and [AudioStream] the player uses. Init and index ranges are kept for [BilibiliDashManifest].
 */
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
                val item = ItagItem(NO_ITAG, ItagItem.ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, label)
                item.codec = format.codecs
                item.bitrate = format.bandwidth
                item.width = format.width
                item.height = format.height
                item.fps = parseFps(format.frameRate)
                item.applyRanges(format)
                VideoStream
                    .Builder()
                    .setId("bilibili-$bvid-video-${format.id}-$index")
                    .setContent(format.url, true)
                    .setMediaFormat(MediaFormat.MPEG_4)
                    .setIsVideoOnly(true)
                    .setResolution(label)
                    .setItagItem(item)
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to build VideoStream for qn=${format.id}: ${e.message}")
                null
            }
        }

    /**
     * FLAC is dropped because it fails to decode on Bilibili's streams (InsufficientCapacityException,
     * per PipePipeClient); Dolby (ec-3) is dropped because Flow has no switch to opt into it.
     * What is left keeps the API's order, and the selector picks the highest bitrate.
     */
    fun convertAudioFormats(
        bvid: String,
        formats: List<BilibiliStreamFormat>,
    ): List<AudioStream> =
        formats
            .filter { isSupportedAudio(it.codecs) }
            .mapIndexedNotNull { index, format ->
                BilibiliMirrors.register(format.url, format.backupUrls)
                try {
                    val item = ItagItem(NO_ITAG, ItagItem.ItagType.AUDIO, MediaFormat.M4A, format.bandwidth)
                    item.codec = format.codecs
                    item.bitrate = format.bandwidth
                    item.applyRanges(format)
                    AudioStream
                        .Builder()
                        .setId("bilibili-$bvid-audio-${format.id}-$index")
                        .setContent(format.url, true)
                        .setMediaFormat(MediaFormat.M4A)
                        .setAverageBitrate(format.bandwidth)
                        .setItagItem(item)
                        .build()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to build AudioStream for qn=${format.id}: ${e.message}")
                    null
                }
            }

    /** Bilibili's quality ids repeat across codecs, so they cannot serve as an itag; -1 is "none". */
    private const val NO_ITAG = -1

    /** True for a stream this bridge built. */
    fun isBilibili(stream: Stream): Boolean = stream.id?.startsWith("bilibili-") == true

    private fun ItagItem.applyRanges(format: BilibiliStreamFormat) {
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
     * "1080p", or "1080p60" for the high-frame-rate ladder. The label is what
     * [VideoCodecUtils.qualityHeightFromStream] reads first, and it must be the tier Bilibili names
     * the stream by (a 720x1280 portrait clip is "720p", not "1280p"), not the raw pixel height.
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

            // An id this table does not know yet: fall back to the shorter side, which is the tier
            // for both landscape and portrait video.
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
