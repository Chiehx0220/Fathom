package io.github.aedev.flow.localserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class HlsVodTest {
    private fun sidx(
        version: Int,
        firstOffset: Long,
        refs: List<Pair<Int, Int>>,
    ): ByteArray {
        val total = 12 + 4 + 4 + (if (version == 0) 8 else 16) + 4 + refs.size * 12
        val b = ByteBuffer.allocate(total)
        b
            .putInt(total)
            .putInt(0x73696478)
            .put(version.toByte())
            .put(0)
            .put(0)
            .put(0)
        b.putInt(1).putInt(1000)
        if (version == 0) b.putInt(0).putInt(firstOffset.toInt()) else b.putLong(0).putLong(firstOffset)
        b.putShort(0).putShort(refs.size.toShort())
        for ((size, dur) in refs) b.putInt(size).putInt(dur).putInt(0x90000000.toInt())
        return b.array()
    }

    @Test
    fun segmentsFollowTheIndexBox() {
        val spans = SidxParser.parse(sidx(0, 0, listOf(1000 to 4000, 2500 to 2000)), indexEnd = 999)
        assertEquals(2, spans.size)
        assertEquals(1000L, spans[0].offset)
        assertEquals(1000L, spans[0].length)
        assertEquals(4.0, spans[0].durationSec, 1e-9)
        assertEquals(2000L, spans[1].offset)
        assertEquals(2500L, spans[1].length)
    }

    @Test
    fun firstOffsetAndVersionOneAreRead() {
        val spans = SidxParser.parse(sidx(1, 50, listOf(10 to 1500)), indexEnd = 99)
        assertEquals(150L, spans[0].offset)
        assertEquals(1.5, spans[0].durationSec, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun otherBoxesAreRefused() {
        SidxParser.parse(ByteArray(64), indexEnd = 10)
    }

    @Test
    fun mediaPlaylistListsInitAndByteRanges() {
        val text =
            HlsVod.media(
                "/stream?rep=v1",
                DashCatalog.ByteRange(0, 739),
                listOf(FragmentSpan(1000, 500, 4.0), FragmentSpan(1500, 700, 5.2)),
            )
        assertTrue(text.contains("#EXT-X-MAP:URI=\"/stream?rep=v1\",BYTERANGE=\"740@0\""))
        assertTrue(text.contains("#EXT-X-BYTERANGE:500@1000"))
        assertTrue(text.contains("#EXT-X-BYTERANGE:700@1500"))
        assertTrue(text.contains("#EXT-X-TARGETDURATION:6"))
        assertTrue(text.trimEnd().endsWith("#EXT-X-ENDLIST"))
    }

    @Test
    fun masterPlaylistNamesVideosAndAudio() {
        val audio = DashCatalog.Audio("a140", "u", 128_000, "mp4a.40.2", DashCatalog.ByteRange(0, 1), DashCatalog.ByteRange(2, 3))
        val track = DashCatalog.AudioTrack("", "en", null, true, false, listOf(audio))
        val video =
            DashCatalog.Video(
                "v137",
                "u",
                4_000_000,
                "avc1.640028",
                1920,
                1080,
                30,
                DashCatalog.ByteRange(0, 1),
                DashCatalog.ByteRange(2, 3),
            )
        val text = HlsVod.master(DashCatalog(listOf(video), listOf(track))) { "/p?rep=$it" }
        assertTrue(text.contains("TYPE=AUDIO,GROUP-ID=\"aud\""))
        assertTrue(text.contains("BANDWIDTH=4128000"))
        assertTrue(text.contains("RESOLUTION=1920x1080,FRAME-RATE=30.000,AUDIO=\"aud\""))
        assertTrue(text.contains("CODECS=\"avc1.640028,mp4a.40.2\""))
        assertTrue(text.contains("\n/p?rep=v137\n"))
    }
}
