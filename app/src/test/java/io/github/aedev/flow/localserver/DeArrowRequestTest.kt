package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeArrowRequestTest {
    @Test
    fun `ids carry the duration that places the random frame`() {
        assertThat(parseDeArrowVideos("dQw4w9WgXcQ:213,9bZkp7q19f0:0"))
            .containsExactly("dQw4w9WgXcQ" to 213, "9bZkp7q19f0" to 0)
            .inOrder()
    }

    @Test
    fun `a missing or bad duration reads as zero`() {
        assertThat(parseDeArrowVideos("dQw4w9WgXcQ,9bZkp7q19f0:x")).containsExactly("dQw4w9WgXcQ" to 0, "9bZkp7q19f0" to 0)
    }

    @Test
    fun `only YouTube ids are kept, each once`() {
        assertThat(parseDeArrowVideos("BV1xx411c7mD:5,dQw4w9WgXcQ:1,dQw4w9WgXcQ:2,,short")).containsExactly("dQw4w9WgXcQ" to 1)
    }

    @Test
    fun `a request is capped`() {
        val ids = (0 until 200).joinToString(",") { "abcdefghi%02d".format(it % 100).take(11) + ":1" }
        assertThat(parseDeArrowVideos(ids).size).isAtMost(60)
        assertThat(parseDeArrowVideos(null)).isEmpty()
    }

    @Test
    fun `the answer names only videos DeArrow changes`() {
        val on =
            DeArrowDto(
                true,
                mapOf(
                    "dQw4w9WgXcQ" to DeArrowItemDto(title = "Plain title"),
                    "9bZkp7q19f0" to DeArrowItemDto(thumbnailUrl = "/image-proxy?u=x"),
                ),
            )
        assertThat(ApiJson.encodeToString(on))
            .isEqualTo(
                """{"enabled":true,"items":{"dQw4w9WgXcQ":{"title":"Plain title"},"9bZkp7q19f0":{"thumbnailUrl":"/image-proxy?u=x"}}}""",
            )
        assertThat(ApiJson.encodeToString(DeArrowDto(false, emptyMap()))).isEqualTo("""{"enabled":false,"items":{}}""")
    }
}
