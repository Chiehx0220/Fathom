package io.github.aedev.flow.localserver.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebSendTest {
    private val watch = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    @Test
    fun `a video link in a share message becomes the watch url`() {
        assertThat(sharedVideoUrl("Look at this https://youtu.be/dQw4w9WgXcQ?si=x")).isEqualTo(watch)
        assertThat(sharedVideoUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=30")).isEqualTo(watch)
    }

    @Test
    fun `a short plays as a watch url too`() {
        assertThat(sharedVideoUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ")).isEqualTo(watch)
    }

    @Test
    fun `anything that is not a video is refused`() {
        assertThat(sharedVideoUrl("https://www.youtube.com/@someone")).isNull()
        assertThat(sharedVideoUrl("https://example.com/watch?v=dQw4w9WgXcQ")).isNull()
        assertThat(sharedVideoUrl("no link here")).isNull()
        assertThat(sharedVideoUrl(null)).isNull()
    }
}
