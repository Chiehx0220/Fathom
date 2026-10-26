package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale
import kotlin.random.Random

class StreamPickTest {
    private class Lists(
        override val videoStreams: List<VideoStream> = emptyList(),
        override val videoOnlyStreams: List<VideoStream> = emptyList(),
        override val audioStreams: List<AudioStream> = emptyList(),
        private val hls: String? = null,
        private val hlsThrows: Boolean = false,
    ) : StreamLists {
        override val length = 0L
        override val hlsUrl: String? get() = if (hlsThrows) error("no hls") else hls
    }

    private fun video(
        url: String,
        itag: Int,
        resolution: String,
    ): VideoStream =
        VideoStream
            .Builder()
            .setId(url)
            .setContent(url, true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setIsVideoOnly(false)
            .setResolution(resolution)
            .setItagItem(ItagItem(itag, ItagItem.ItagType.VIDEO, MediaFormat.MPEG_4, resolution))
            .build()

    private fun audio(
        url: String,
        itag: Int,
        bitrate: Int,
        trackId: String?,
        original: Boolean,
    ): AudioStream =
        AudioStream
            .Builder()
            .setId(url)
            .setContent(url, true)
            .setMediaFormat(MediaFormat.M4A)
            .setAverageBitrate(bitrate)
            .setAudioTrackId(trackId)
            .setAudioTrackType(if (original) AudioTrackType.ORIGINAL else AudioTrackType.DUBBED)
            .setAudioLocale(trackId?.let(Locale::forLanguageTag))
            .setItagItem(ItagItem(itag, ItagItem.ItagType.AUDIO, MediaFormat.M4A, "${bitrate}kbps"))
            .build()

    // pre-StreamPick resolution, verbatim
    private fun legacy(
        extractor: StreamLists,
        requestedItag: Int,
        requestedTrackId: String?,
        mediaType: String?,
        targetQuality: String,
    ): String? {
        if (requestedItag == -1 && mediaType != null) {
            if ("audio" == mediaType) {
                val audioOnly = extractor.audioStreams
                if (audioOnly.isNotEmpty()) {
                    var best = audioOnly[0]
                    for (candidate in audioOnly) {
                        if (candidate.averageBitrate > best.averageBitrate) best = candidate
                    }
                    return best.content
                }
            } else if ("video" == mediaType) {
                val videoOnly = extractor.videoOnlyStreams.ifEmpty { extractor.videoStreams }
                if (videoOnly.isNotEmpty()) return videoOnly[0].content
            }
            return null
        }
        if (requestedItag != -1) {
            for (stream in extractor.videoStreams) if (stream.itag == requestedItag) return stream.content
            for (stream in extractor.videoOnlyStreams) if (stream.itag == requestedItag) return stream.content
            for (stream in extractor.audioStreams) {
                if (stream.itag == requestedItag && (stream.audioTrackId ?: "") == (requestedTrackId ?: "")) return stream.content
            }
        }
        val targetHeight = LocalServerMedia.getResolutionHeight(targetQuality)
        val progressive = extractor.videoStreams
        if (progressive.isNotEmpty()) {
            var selected: VideoStream? = null
            var bestHeight = -1
            for (stream in progressive) {
                val height = LocalServerMedia.getResolutionHeight(stream.resolution)
                if (height <= targetHeight && height > bestHeight) {
                    bestHeight = height
                    selected = stream
                }
            }
            if (selected == null) {
                for (stream in progressive) {
                    val height = LocalServerMedia.getResolutionHeight(stream.resolution)
                    if (height > bestHeight) {
                        bestHeight = height
                        selected = stream
                    }
                }
            }
            return (selected ?: progressive[0]).content
        }
        try {
            val hlsUrl = extractor.hlsUrl
            if (!hlsUrl.isNullOrEmpty()) return hlsUrl
        } catch (e: Exception) {
        }
        val rawAudio = extractor.audioStreams
        if (rawAudio.isNotEmpty()) {
            var sorted: MutableList<AudioStream> = ArrayList(rawAudio)
            sorted.sortWith(LocalServerMedia.audioTrackPriorityComparator())
            val bestTrackId = sorted[0].audioTrackId
            val filtered = sorted.filter { it.audioTrackId == bestTrackId }
            if (filtered.isNotEmpty()) sorted = filtered.toMutableList()
            return sorted[0].content
        }
        return null
    }

    private val resolutions = listOf("144p", "360p", "720p", "720p60", "1080p", "2160p", "audio", "")
    private val qualities = listOf("360p", "720p", "1080p", "144p", "")
    private val tracks = listOf(null, "en", "ja", "de")
    private val mediaTypes = listOf(null, "audio", "video", "other")

    private fun randomLists(random: Random): Lists {
        var n = 0

        fun videos() = List(random.nextInt(0, 4)) { video("v${n++}", random.nextInt(18, 24), resolutions.random(random)) }

        return Lists(
            videos(),
            videos(),
            List(random.nextInt(0, 4)) {
                audio("a${n++}", random.nextInt(18, 24), random.nextInt(0, 3) * 64, tracks.random(random), random.nextBoolean())
            },
            hls = listOf(null, "", "https://hls.invalid/m.m3u8").random(random),
            hlsThrows = random.nextInt(0, 5) == 0,
        )
    }

    @Test
    fun `matches the pre-extraction resolution over random stream sets`() {
        val random = Random(20261026)
        repeat(3000) { case ->
            val lists = randomLists(random)
            val itag = if (random.nextBoolean()) -1 else random.nextInt(18, 24)
            val trackId = tracks.random(random)
            val mediaType = mediaTypes.random(random)
            val quality = qualities.random(random)

            val expected = legacy(lists, itag, trackId, mediaType, quality)
            val actual = pickStreamUrl(lists, itag, trackId, mediaType) { quality }

            assertWithMessage("case $case itag=$itag track=$trackId type=$mediaType q=$quality").that(actual).isEqualTo(expected)
        }
    }

    @Test
    fun `quality is only read once the itag lookup misses`() {
        val lists = Lists(videoStreams = listOf(video("hit", 22, "720p")))

        val url = pickStreamUrl(lists, 22, null, null) { error("quality read") }

        assertThat(url).isEqualTo("hit")
    }
}
