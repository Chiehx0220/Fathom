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
import org.schabi.newpipe.extractor.stream.AudioStream
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
                val builder =
                    VideoStream.Builder()
                        .setId("bilibili-$bvid-video-${format.id}-$index")
                        .setContent(format.url, true)
                        .setMediaFormat(MediaFormat.MPEG_4)
                        .setCodec(format.codecs)
                        .setBitrate(format.bandwidth)
                        .setWidth(format.width)
                        .setHeight(format.height)
                        .setFps(parseFps(format.frameRate))
                        .setIsVideoOnly(true)
                        .setResolution(resolutionLabel(format))
                format.initRange?.let { builder.setInitStart(it.first.toInt()).setInitEnd(it.last.toInt()) }
                format.indexRange?.let { builder.setIndexStart(it.first.toInt()).setIndexEnd(it.last.toInt()) }
                builder.build()
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
                    val builder =
                        AudioStream.Builder()
                            .setId("bilibili-$bvid-audio-${format.id}-$index")
                            .setContent(format.url, true)
                            .setMediaFormat(MediaFormat.M4A)
                            .setCodec(format.codecs)
                            .setBitrate(format.bandwidth)
                            .setAverageBitrate(format.bandwidth)
                    format.initRange?.let { builder.setInitStart(it.first.toInt()).setInitEnd(it.last.toInt()) }
                    format.indexRange?.let { builder.setIndexStart(it.first.toInt()).setIndexEnd(it.last.toInt()) }
                    builder.build()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to build AudioStream for qn=${format.id}: ${e.message}")
                    null
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
