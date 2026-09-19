/*
 * Ported from PipePipeClient (GPL-3.0): app/src/main/java/org/schabi/newpipe/player/resolver/
 * PlaybackResolver.java, createBiliBiliDashManifest.
 */
package io.github.aedev.flow.player.stream

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * A one-representation DASH manifest for one Bilibili stream, built from the init and index ranges
 * the API reports, as PipePipe does. Played as a plain file, one long request gets throttled by the
 * CDN; through DASH the player asks for short ranges, which the CDN serves fast.
 */
object BilibiliDashManifest {
    fun forVideo(
        stream: VideoStream,
        durationSeconds: Long,
    ): String? {
        val extra =
            (if (stream.width > 0) " width=\"${stream.width}\"" else "") +
                (if (stream.height > 0) " height=\"${stream.height}\"" else "") +
                (if (stream.fps > 0) " frameRate=\"${stream.fps}\"" else "")
        return build(
            stream = stream,
            contentType = "video",
            mimeType = "video/mp4",
            codecs = stream.codec,
            bandwidth = stream.bitrate,
            initStart = stream.initStart,
            initEnd = stream.initEnd,
            indexStart = stream.indexStart,
            indexEnd = stream.indexEnd,
            extraAttributes = extra,
            durationSeconds = durationSeconds,
        )
    }

    fun forAudio(
        stream: AudioStream,
        durationSeconds: Long,
    ): String? =
        build(
            stream = stream,
            contentType = "audio",
            mimeType = "audio/mp4",
            codecs = stream.codec,
            bandwidth = if (stream.bitrate > 0) stream.bitrate else stream.averageBitrate,
            initStart = stream.initStart,
            initEnd = stream.initEnd,
            indexStart = stream.indexStart,
            indexEnd = stream.indexEnd,
            extraAttributes = "",
            durationSeconds = durationSeconds,
        )

    /** Null when the stream carries no usable ranges, i.e. it is not one of Bilibili's DASH files. */
    @Suppress("LongParameterList")
    private fun build(
        stream: Stream,
        contentType: String,
        mimeType: String,
        codecs: String?,
        bandwidth: Int,
        initStart: Int,
        initEnd: Int,
        indexStart: Int,
        indexEnd: Int,
        extraAttributes: String,
        durationSeconds: Long,
    ): String? {
        if (initEnd <= initStart || indexEnd <= indexStart || bandwidth <= 0 || codecs.isNullOrEmpty()) return null
        val url = stream.content?.takeIf { it.isNotEmpty() } ?: return null
        val duration = maxOf(1L, durationSeconds)
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" type=\"static\" " +
            "profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" minBufferTime=\"PT1.5S\" " +
            "mediaPresentationDuration=\"PT${duration}S\">" +
            "<Period duration=\"PT${duration}S\">" +
            "<AdaptationSet contentType=\"$contentType\" mimeType=\"$mimeType\" subsegmentAlignment=\"true\">" +
            "<Representation id=\"${escapeXml(stream.id.orEmpty())}\" bandwidth=\"$bandwidth\" " +
            "codecs=\"${escapeXml(codecs)}\"$extraAttributes>" +
            "<BaseURL>${escapeXml(url)}</BaseURL>" +
            "<SegmentBase indexRange=\"$indexStart-$indexEnd\">" +
            "<Initialization range=\"$initStart-$initEnd\"/>" +
            "</SegmentBase>" +
            "</Representation></AdaptationSet></Period></MPD>"
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
