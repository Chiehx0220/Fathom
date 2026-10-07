package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.testing.DefaultLocaleRule
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import java.util.Locale

class LocalServerMediaTest {
    @get:Rule
    val locale = DefaultLocaleRule(Locale.ENGLISH)

    @Test
    fun `a video id is taken from a watch, shorts or short link`() {
        assertThat(LocalServerMedia.getVideoId("https://www.youtube.com/watch?v=abc123&t=5")).isEqualTo("abc123")
        assertThat(LocalServerMedia.getVideoId("https://www.youtube.com/watch?v=abc123")).isEqualTo("abc123")
        assertThat(LocalServerMedia.getVideoId("https://www.bilibili.com/video/BV1YtHs62EHZ?p=1")).isEqualTo("BV1YtHs62EHZ?p=1")
        assertThat(LocalServerMedia.getVideoId("https://www.bilibili.com/video/https://www.bilibili.com/video/BV1YtHs62EHZ?p=2"))
            .isEqualTo("BV1YtHs62EHZ?p=2")
        assertThat(LocalServerMedia.getVideoId("https://live.bilibili.com/21144080?spm=1")).isEqualTo("live:21144080")
        assertThat(LocalServerMedia.getVideoId("BV1YtHs62EHZ?p=1")).isEqualTo("BV1YtHs62EHZ?p=1")
        assertThat(LocalServerMedia.getVideoId("https://www.youtube.com/shorts/xyz789?feature=share")).isEqualTo("xyz789")
        assertThat(LocalServerMedia.getVideoId("https://youtu.be/def456?si=1")).isEqualTo("def456")
    }

    @Test
    fun `anything else is taken as the id itself, and nothing gives nothing`() {
        assertThat(LocalServerMedia.getVideoId("BV1xx411c7mD?p=2")).isEqualTo("BV1xx411c7mD?p=2")
        assertThat(LocalServerMedia.getVideoId(null)).isEmpty()
    }

    @Test
    fun `only YouTube and Bilibili are served`() {
        assertThat(LocalServerMedia.isSupportedService(0)).isTrue()
        assertThat(LocalServerMedia.isSupportedService(5)).isTrue()
        assertThat(LocalServerMedia.isSupportedService(1)).isFalse()
    }

    @Test
    fun `a bare mp4a codec becomes AAC-LC and a full one is left alone`() {
        assertThat(LocalServerMedia.normalizeAudioCodec(null)).isEqualTo("mp4a.40.2")
        assertThat(LocalServerMedia.normalizeAudioCodec("  ")).isEqualTo("mp4a.40.2")
        assertThat(LocalServerMedia.normalizeAudioCodec("mp4a")).isEqualTo("mp4a.40.2")
        assertThat(LocalServerMedia.normalizeAudioCodec(" opus ")).isEqualTo("opus")
    }

    @Test
    fun `the height is read from a resolution label, ignoring the frame rate`() {
        assertThat(LocalServerMedia.getResolutionHeight("720p60")).isEqualTo(720)
        assertThat(LocalServerMedia.getResolutionHeight("1080p")).isEqualTo(1080)
        assertThat(LocalServerMedia.getResolutionHeight("audio")).isEqualTo(0)
        assertThat(LocalServerMedia.getResolutionHeight("")).isEqualTo(0)
        assertThat(LocalServerMedia.getResolutionHeight(null)).isEqualTo(0)
    }

    private fun track(
        original: Boolean = false,
        language: String? = null,
        bitrate: Int = 0,
    ): AudioStream =
        mockk {
            every { audioTrackType } returns if (original) AudioTrackType.ORIGINAL else AudioTrackType.DUBBED
            every { audioLocale } returns language?.let(Locale::forLanguageTag)
            every { averageBitrate } returns bitrate
            every { this@mockk.bitrate } returns 0
        }

    @Test
    fun `audio tracks are ordered original, then the device language, then English, then bitrate`() {
        val original = track(original = true, language = "ja", bitrate = 64)
        val english = track(language = "en", bitrate = 128)
        val german = track(language = "de", bitrate = 256)
        val highest = track(language = "fr", bitrate = 320)

        val ordered = listOf(highest, german, english, original).sortedWith(LocalServerMedia.audioTrackPriorityComparator())

        assertThat(ordered).containsExactly(original, english, highest, german).inOrder()
    }

    @Test
    fun `the device language wins over English among dubs`() {
        Locale.setDefault(Locale.GERMAN)
        val english = track(language = "en", bitrate = 128)
        val german = track(language = "de", bitrate = 64)

        val ordered = listOf(english, german).sortedWith(LocalServerMedia.audioTrackPriorityComparator())

        assertThat(ordered).containsExactly(german, english).inOrder()
    }
}
