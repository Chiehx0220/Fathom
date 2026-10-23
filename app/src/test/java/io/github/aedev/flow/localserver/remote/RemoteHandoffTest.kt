package io.github.aedev.flow.localserver.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RemoteHandoffTest {
    @Test
    fun `the command carries the second to start at and the full url`() {
        assertThat(playVideoAtCommand("https://www.youtube.com/watch?v=abc", 61_540L))
            .isEqualTo("play_video_at:61.5:https://www.youtube.com/watch?v=abc")
    }

    @Test
    fun `a negative position starts at the beginning`() {
        assertThat(playVideoAtCommand("https://www.youtube.com/watch?v=abc", -5L)).startsWith("play_video_at:0.0:")
    }
}
