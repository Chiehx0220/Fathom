package io.github.aedev.flow.localserver

import java.nio.ByteBuffer
import java.util.Locale

/** One media segment of an fMP4 file: where it sits in the file and how long it plays. */
internal class FragmentSpan(
    val offset: Long,
    val length: Long,
    val durationSec: Double,
)

/**
 * Reads a `sidx` box (the segment index at the `indexRange` of a DASH-style MP4) into the byte spans of its segments.
 * [indexEnd] is the last byte of the box in the file: the first segment follows it, plus the box's own first offset.
 */
internal object SidxParser {
    fun parse(
        sidx: ByteArray,
        indexEnd: Long,
    ): List<FragmentSpan> {
        val buf = ByteBuffer.wrap(sidx)
        require(sidx.size >= 32 && buf.getInt(4) == 0x73696478) { "not a sidx box" }
        val version = sidx[8].toInt()
        val timescale = buf.getInt(16).toLong() and 0xFFFFFFFFL
        require(timescale > 0) { "sidx without a timescale" }
        var pos = 20
        val firstOffset: Long
        if (version == 0) {
            firstOffset = buf.getInt(pos + 4).toLong() and 0xFFFFFFFFL
            pos += 8
        } else {
            firstOffset = buf.getLong(pos + 8)
            pos += 16
        }
        val count = buf.getShort(pos + 2).toInt() and 0xFFFF
        pos += 4
        require(sidx.size >= pos + count * 12) { "sidx is cut short" }
        var offset = indexEnd + 1 + firstOffset
        val spans = ArrayList<FragmentSpan>(count)
        repeat(count) {
            val word = buf.getInt(pos)
            // A reference to another sidx (top bit set) is not a media segment; players of this kind never meet one.
            require(word >= 0) { "nested sidx" }
            val size = word.toLong()
            val duration = (buf.getInt(pos + 4).toLong() and 0xFFFFFFFFL).toDouble() / timescale
            spans.add(FragmentSpan(offset, size, duration))
            offset += size
            pos += 12
        }
        return spans
    }
}

/** Writes a [DashCatalog] as HLS playlists (fMP4 segments addressed by byte range, so the files are not copied or cut). */
internal object HlsVod {
    private const val AUDIO_GROUP = "aud"

    /** [playlistUrl] is the address of a representation's media playlist. */
    fun master(
        catalog: DashCatalog,
        playlistUrl: (String) -> String,
    ): String {
        val out = StringBuilder("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-INDEPENDENT-SEGMENTS\n")
        val tracks = catalog.audioTracks.mapNotNull { track -> track.audios.firstOrNull()?.let { track to it } }
        tracks.forEachIndexed { i, (track, audio) ->
            out.append(
                "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"$AUDIO_GROUP\",NAME=\"${attr(track.label ?: track.language ?: "Audio ${i + 1}")}\"",
            )
            track.language?.let { out.append(",LANGUAGE=\"${attr(it)}\"") }
            out.append(",DEFAULT=${if (i == 0) "YES" else "NO"},AUTOSELECT=YES,URI=\"${attr(playlistUrl(audio.id))}\"\n")
        }
        val audioCodec = tracks.firstOrNull()?.second?.codec
        for (v in catalog.videos) {
            val audioBandwidth = tracks.firstOrNull()?.second?.bandwidth ?: 0L
            out.append("#EXT-X-STREAM-INF:BANDWIDTH=${v.bandwidth + audioBandwidth},AVERAGE-BANDWIDTH=${v.bandwidth + audioBandwidth}")
            out.append(",CODECS=\"${attr(listOfNotNull(v.codec, audioCodec).joinToString(","))}\"")
            if (v.width > 0 && v.height > 0) out.append(",RESOLUTION=${v.width}x${v.height}")
            if (v.fps > 0) out.append(",FRAME-RATE=${v.fps}.000")
            if (tracks.isNotEmpty()) out.append(",AUDIO=\"$AUDIO_GROUP\"")
            out.append("\n").append(playlistUrl(v.id)).append("\n")
        }
        return out.toString()
    }

    /** The playlist of one file: its `init` and the [spans] of its segments, all as byte ranges of [streamUrl]. */
    fun media(
        streamUrl: String,
        init: DashCatalog.ByteRange,
        spans: List<FragmentSpan>,
    ): String {
        val target =
            kotlin.math
                .ceil(spans.maxOfOrNull { it.durationSec } ?: 1.0)
                .toInt()
                .coerceAtLeast(1)
        val out =
            StringBuilder("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:$target\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-MEDIA-SEQUENCE:0\n")
        out.append("#EXT-X-MAP:URI=\"${attr(streamUrl)}\",BYTERANGE=\"${init.end - init.start + 1}@${init.start}\"\n")
        for (s in spans) {
            out.append("#EXTINF:").append(String.format(Locale.US, "%.5f", s.durationSec)).append(",\n")
            out.append("#EXT-X-BYTERANGE:${s.length}@${s.offset}\n").append(streamUrl).append("\n")
        }
        return out.append("#EXT-X-ENDLIST\n").toString()
    }

    private fun attr(text: String) = text.replace("\"", "%22").replace("\n", " ")
}
