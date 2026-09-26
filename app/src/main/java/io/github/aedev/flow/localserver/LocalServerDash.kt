package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.net.URLEncoder
import java.util.Locale

/**
 * What the web player can be offered for one video: the qualities and audio tracks, each with a short id of its own.
 *
 * The manifest, and the `/stream` route the player then fetches from, both work from this one list. A `/stream?rep=` request
 * names a representation by that id, so nothing depends on an itag being unique (Bilibili has none) or on video and audio
 * agreeing which stream an itag meant.
 */
internal class DashCatalog(
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

    /** One language or dub: its own AdaptationSet, so the player lists it as an audio track. */
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
        // A quality ladder of fewer rungs than this is not worth switching a player's codec for.
        private const val MIN_LADDER = 3

        /**
         * Reads the catalog out of [streams]. [preferredTrack] is the audio track to start on (the player takes the first);
         * without one, the best track by [LocalHttpServer.audioTrackPriorityComparator].
         *
         * All the video sits in one AdaptationSet, and a player cannot move between codecs inside one set, so only one codec
         * family is offered. Normally that is H.264, which every device plays; with [highest] it is whichever family reaches the
         * tallest picture (YouTube's 1440p and 4K come as AV1), for a player that has said it can decode that smoothly.
         */
        fun of(
            streams: StreamLists,
            preferredTrack: String? = null,
            highest: Boolean = false,
        ): DashCatalog {
            val (rawVideo, rawAudio) = synchronized(streams) { streams.videoOnlyStreams to streams.audioStreams }
            return DashCatalog(pickFamily(playableVideos(rawVideo), highest), audioTracks(rawAudio, preferredTrack))
        }

        /** The address of representation [repId], of any quality: a player may ask for one the default offer would leave out. */
        fun urlOf(
            streams: StreamLists,
            repId: String,
        ): String? {
            val (rawVideo, rawAudio) = synchronized(streams) { streams.videoOnlyStreams to streams.audioStreams }
            return playableVideos(rawVideo).firstOrNull { it.id == repId }?.url
                ?: audioTracks(rawAudio, null).firstNotNullOfOrNull { track -> track.audios.firstOrNull { it.id == repId }?.url }
        }

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
                        .maxWithOrNull(
                            compareBy<Map.Entry<String, List<Video>>>({ e -> e.value.maxOf { it.height } }, { e ->
                                if (e.key ==
                                    "avc1"
                                ) {
                                    1
                                } else {
                                    0
                                }
                            }),
                        )?.key
                } else {
                    null
                }
            val family = chosen ?: if (families.containsKey("avc1")) "avc1" else families.maxByOrNull { it.value.size }!!.key
            return families.getValue(family)
        }

        /** Every MP4 video quality with an index, whatever its codec, each with its id. */
        private fun playableVideos(streams: List<VideoStream>): List<Video> {
            val seen = HashSet<Int>()
            val ids = HashSet<String>()
            return streams
                .filter { it.format == MediaFormat.MPEG_4 && it.hasIndex() && (it.itag <= 0 || seen.add(it.itag)) }
                .sortedWith(compareBy<VideoStream>({ it.height }, { it.fps }, { it.bitrate }))
                .mapNotNull { vs ->
                    val url = vs.content?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val bandwidth = bandwidthBps(vs.bitrate.toLong(), fallbackBps = 1_000_000L)
                    // An itag is unique per quality on YouTube; Bilibili has none, so its qualities are told apart by what they are.
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
            val byPriority = m4a.sortedWith(LocalHttpServer.audioTrackPriorityComparator())
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
                        // The same itag comes once per language, so the track is part of the id.
                        val id =
                            (if (stream.itag > 0) "a${stream.itag}" else "a_$bandwidth") + (if (trackId.isNotEmpty()) "_$trackId" else "")
                        if (!ids.add(id)) return@mapNotNull null
                        Audio(
                            id,
                            url,
                            bandwidth,
                            LocalHttpServer.normalizeAudioCodec(stream.codec),
                            ByteRange(stream.initStart.toLong(), stream.initEnd.toLong()),
                            ByteRange(stream.indexStart.toLong(), stream.indexEnd.toLong()),
                        )
                    }
                if (audios.isEmpty()) return@mapNotNull null
                AudioTrack(
                    trackId = trackId,
                    language = first.audioLocale?.toString() ?: trackId.takeIf { it.isNotEmpty() }?.substringBefore('.'),
                    label = first.audioTrackName?.takeIf { it.isNotEmpty() },
                    original = LocalHttpServer.isOriginalAudioTrack(first),
                    hasRole = first.audioTrackId != null,
                    audios = audios,
                )
            }
        }

        private fun VideoStream.hasIndex() = initStart >= 0 && initEnd >= 0 && indexStart >= 0 && indexEnd >= 0

        private fun AudioStream.hasIndex() = initStart >= 0 && initEnd >= 0 && indexStart >= 0 && indexEnd >= 0

        private fun AudioStream.bitrateOf() = if (averageBitrate > 0) averageBitrate else bitrate

        /**
         * A Representation's `bandwidth`, in bps. NewPipe's static itag table, its fallback when a format carries no live
         * bitrate, reports audio in kbps (at most 256); a live bitrate is never that low. A raw value under 5000 is that
         * kbps leftover and is rescaled, anything at or above it is already bps.
         */
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

/** Writes a [DashCatalog] as an MPEG-DASH manifest (on-demand profile, a byte-range index per representation). */
internal object DashManifest {
    // The manifest needs a length; a service that reports none gets a placeholder long enough not to end the video early.
    private const val UNKNOWN_DURATION_SEC = 1800.0

    /** [streamUrl] turns a representation id into the address the player fetches it from. */
    fun write(
        catalog: DashCatalog,
        durationSec: Double,
        streamUrl: (String) -> String,
    ): String {
        val duration = "PT" + String.format(Locale.US, "%.3f", if (durationSec > 0) durationSec else UNKNOWN_DURATION_SEC) + "S"
        val xml = StringBuilder()
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        xml.append(
            "<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" type=\"static\" mediaPresentationDuration=\"$duration\" minBufferTime=\"PT1.5S\">\n",
        )
        xml.append("  <Period duration=\"$duration\">\n")

        var setId = 0
        if (catalog.videos.isNotEmpty()) {
            xml.append(
                "    <AdaptationSet id=\"${setId++}\" mimeType=\"video/mp4\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\n",
            )
            for (v in catalog.videos) {
                xml.append("      <Representation id=\"${esc(v.id)}\" bandwidth=\"${v.bandwidth}\" codecs=\"${esc(v.codec)}\"")
                if (v.width > 0 && v.height > 0) xml.append(" width=\"${v.width}\" height=\"${v.height}\"")
                if (v.fps > 0) xml.append(" frameRate=\"${v.fps}\"")
                xml.append(" sar=\"1:1\">\n")
                representationBody(xml, streamUrl(v.id), v.index, v.init)
                xml.append("      </Representation>\n")
            }
            xml.append("    </AdaptationSet>\n")
        }

        for (track in catalog.audioTracks) {
            xml.append(
                "    <AdaptationSet id=\"${setId++}\" mimeType=\"audio/mp4\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\"",
            )
            track.language?.let { xml.append(" lang=\"${esc(it)}\"") }
            track.label?.let { xml.append(" label=\"${esc(it)}\"") }
            xml.append(">\n")
            // Any track that is not the original is a dub.
            if (track.hasRole) {
                xml.append(
                    "      <Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"${if (track.original) "main" else "dub"}\"/>\n",
                )
            }
            for (a in track.audios) {
                xml.append(
                    "      <Representation id=\"${esc(
                        a.id,
                    )}\" bandwidth=\"${a.bandwidth}\" codecs=\"${esc(a.codec)}\" audioSamplingRate=\"44100\">\n",
                )
                xml.append(
                    "        <AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\"/>\n",
                )
                representationBody(xml, streamUrl(a.id), a.index, a.init)
                xml.append("      </Representation>\n")
            }
            xml.append("    </AdaptationSet>\n")
        }

        xml.append("  </Period>\n")
        xml.append("</MPD>\n")
        return xml.toString()
    }

    private fun representationBody(
        xml: StringBuilder,
        url: String,
        index: DashCatalog.ByteRange,
        init: DashCatalog.ByteRange,
    ) {
        xml.append("        <BaseURL>${esc(url)}</BaseURL>\n")
        xml.append("        <SegmentBase indexRange=\"$index\" indexRangeExact=\"true\">\n")
        xml.append("          <Initialization range=\"$init\"/>\n")
        xml.append("        </SegmentBase>\n")
    }

    private fun esc(text: String) =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    /** The `/stream` address of a representation. */
    fun streamPath(
        serviceId: Int,
        mediaUrl: String,
        repId: String,
    ): String = "/stream?serviceId=$serviceId&id=${URLEncoder.encode(mediaUrl, "UTF-8")}&rep=${URLEncoder.encode(repId, "UTF-8")}"
}
