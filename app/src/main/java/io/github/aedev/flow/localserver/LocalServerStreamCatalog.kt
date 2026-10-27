package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.URLEncoder

/** Web-player offer: qualities + audio tracks with short ids; `/stream?rep=` addresses by id (Bilibili has no unique itag). */
internal class StreamCatalog(
    val videos: List<Video>,
    val audioTracks: List<AudioTrack>,
) {
    class ByteRange(
        val start: Long,
        val end: Long,
    ) {
        override fun toString() = "$start-$end"
    }

    class Video(
        val id: String,
        val url: String,
        val bandwidth: Long,
        val codec: String,
        val width: Int,
        val height: Int,
        val fps: Int,
        val init: ByteRange,
        val index: ByteRange,
    ) {
        val family: String get() = codec.substringBefore('.')
    }

    class Audio(
        val id: String,
        val url: String,
        val bandwidth: Long,
        val codec: String,
        val init: ByteRange,
        val index: ByteRange,
    )

    /** One language/dub: own AdaptationSet (player audio track). */
    class AudioTrack(
        val trackId: String,
        val language: String?,
        val label: String?,
        val original: Boolean,
        val hasRole: Boolean,
        val audios: List<Audio>,
    )

    fun urlOf(repId: String): String? =
        videos.firstOrNull { it.id == repId }?.url
            ?: audioTracks.firstNotNullOfOrNull { track -> track.audios.firstOrNull { it.id == repId }?.url }

    companion object {
        // Ladder shorter than this: codec switch not worth it.
        private const val MIN_LADDER = 3

        /** Catalog from [streams]; [preferredTrack] first. One codec family: H.264, [highest] = tallest (AV1 for 1440p/4K). */
        fun of(
            streams: StreamLists,
            preferredTrack: String? = null,
            highest: Boolean = false,
        ): StreamCatalog {
            val (rawVideo, rawAudio) = synchronized(streams) { streams.videoOnlyStreams to streams.audioStreams }
            return StreamCatalog(pickFamily(playableVideos(rawVideo), highest), audioTracks(rawAudio, preferredTrack))
        }

        /** Address of representation [repId], any quality (may be outside default offer). */
        fun urlOf(
            streams: StreamLists,
            repId: String,
        ): String? {
            val (rawVideo, rawAudio) = synchronized(streams) { streams.videoOnlyStreams to streams.audioStreams }
            return playableVideos(rawVideo).firstOrNull { it.id == repId }?.url
                ?: audioTracks(rawAudio, null).firstNotNullOfOrNull { track -> track.audios.firstOrNull { it.id == repId }?.url }
        }

        private val tallestThenAvc =
            compareBy<Map.Entry<String, List<Video>>>({ e -> e.value.maxOf { it.height } }, { e -> if (e.key == "avc1") 1 else 0 })

        private fun pickFamily(
            all: List<Video>,
            highest: Boolean,
        ): List<Video> {
            val families = all.groupBy { it.family }
            if (families.isEmpty()) return emptyList()
            val chosen =
                if (highest) {
                    families.entries
                        .filter { it.value.size >= MIN_LADDER }
                        .maxWithOrNull(tallestThenAvc)
                        ?.key
                } else {
                    null
                }
            val family = chosen ?: if ("avc1" in families) "avc1" else families.maxBy { it.value.size }.key
            return families.getValue(family)
        }

        /** Every indexed MP4 video quality, any codec, with ids. */
        private fun playableVideos(streams: List<VideoStream>): List<Video> {
            val seen = HashSet<Int>()
            val ids = HashSet<String>()
            return streams
                .filter { it.format == MediaFormat.MPEG_4 && it.hasIndex() && (it.itag <= 0 || seen.add(it.itag)) }
                .sortedWith(compareBy<VideoStream>({ it.height }, { it.fps }, { it.bitrate }))
                .mapNotNull { vs ->
                    val url = vs.content?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val bandwidth = bandwidthBps(vs.bitrate.toLong(), fallbackBps = 1_000_000L)
                    // YouTube itag unique per quality; Bilibili none: qualities told apart by attributes.
                    val id = if (vs.itag > 0) "v${vs.itag}" else "v${vs.height}p${vs.fps}_$bandwidth"
                    if (!ids.add(id)) return@mapNotNull null
                    Video(
                        id,
                        url,
                        bandwidth,
                        vs.codec.orEmpty(),
                        vs.width.toInt(),
                        vs.height.toInt(),
                        vs.fps.toInt(),
                        ByteRange(vs.initStart.toLong(), vs.initEnd.toLong()),
                        ByteRange(vs.indexStart.toLong(), vs.indexEnd.toLong()),
                    )
                }
        }

        private fun audioTracks(
            streams: List<AudioStream>,
            preferredTrack: String?,
        ): List<AudioTrack> {
            val m4a = streams.filter { it.format == MediaFormat.M4A && it.hasIndex() }
            if (m4a.isEmpty()) return emptyList()
            val byPriority = m4a.sortedWith(LocalServerMedia.audioTrackPriorityComparator())
            val order = LinkedHashSet<String>()
            order.add(preferredTrack ?: (byPriority[0].audioTrackId ?: ""))
            for (stream in byPriority) order.add(stream.audioTrackId ?: "")

            return order.mapNotNull { trackId ->
                val members = m4a.filter { (it.audioTrackId ?: "") == trackId }.sortedByDescending { it.bitrateOf() }
                if (members.isEmpty()) return@mapNotNull null
                val first = members[0]
                val ids = HashSet<String>()
                val audios =
                    members.mapNotNull { stream ->
                        val url = stream.content?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        val bandwidth = bandwidthBps(stream.bitrateOf().toLong(), fallbackBps = 128_000L)
                        // Same itag once per language: track in id.
                        val id =
                            (if (stream.itag > 0) "a${stream.itag}" else "a_$bandwidth") + (if (trackId.isNotEmpty()) "_$trackId" else "")
                        if (!ids.add(id)) return@mapNotNull null
                        Audio(
                            id,
                            url,
                            bandwidth,
                            LocalServerMedia.normalizeAudioCodec(stream.codec),
                            ByteRange(stream.initStart.toLong(), stream.initEnd.toLong()),
                            ByteRange(stream.indexStart.toLong(), stream.indexEnd.toLong()),
                        )
                    }
                if (audios.isEmpty()) return@mapNotNull null
                AudioTrack(
                    trackId = trackId,
                    language = first.audioLocale?.toString() ?: trackId.takeIf { it.isNotEmpty() }?.substringBefore('.'),
                    label = first.audioTrackName?.takeIf { it.isNotEmpty() },
                    original = LocalServerMedia.isOriginalAudioTrack(first),
                    hasRole = first.audioTrackId != null,
                    audios = audios,
                )
            }
        }

        private fun VideoStream.hasIndex() = initStart >= 0 && initEnd >= 0 && indexStart >= 0 && indexEnd >= 0

        private fun AudioStream.hasIndex() = initStart >= 0 && initEnd >= 0 && indexStart >= 0 && indexEnd >= 0

        private fun AudioStream.bitrateOf() = if (averageBitrate > 0) averageBitrate else bitrate

        /** Representation bandwidth, bps; raw < 5000 = kbps from NewPipe static itag table, rescaled. */
        private fun bandwidthBps(
            raw: Long,
            fallbackBps: Long,
        ): Long {
            var bps = if (raw > 0) raw else fallbackBps
            if (bps < 5000) bps *= 1000
            return bps
        }
    }
}

/** `/stream` address of a representation. */
internal object StreamPath {
    fun of(
        serviceId: Int,
        mediaUrl: String,
        repId: String,
    ): String = "/stream?serviceId=$serviceId&id=${URLEncoder.encode(mediaUrl, "UTF-8")}&rep=${URLEncoder.encode(repId, "UTF-8")}"
}
