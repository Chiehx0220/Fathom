package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WireShapeTest {
    private fun json(body: Any) =
        when (body) {
            is Bullet -> ApiJson.encodeToString(body)
            is LiveChat -> ApiJson.encodeToString(body)
            is Status -> ApiJson.encodeToString(body)
            is ButtonState -> ApiJson.encodeToString(body)
            else -> error("unmapped")
        }

    @Test
    fun `danmaku carries time and size, live chat does not`() {
        assertThat(
            json(Bullet("hi", "#FF0000", "top", 1.5, 1.0f)),
        ).isEqualTo("""{"text":"hi","color":"#FF0000","position":"top","time":1.5,"size":1.0}""")
        assertThat(json(LiveChat(3, listOf(Bullet("hi", "#FF0000", "scroll")))))
            .isEqualTo("""{"seq":3,"items":[{"text":"hi","color":"#FF0000","position":"scroll"}]}""")
    }

    @Test
    fun `status names the lock code release_code and omits absent fields`() {
        assertThat(json(Status("success", releaseCode = "abc"))).isEqualTo("""{"status":"success","release_code":"abc"}""")
        assertThat(json(SUCCESS)).isEqualTo("""{"status":"success"}""")
    }

    @Test
    fun `an unrated video reports like as null`() {
        assertThat(json(ButtonState(false, false, false, null.orJsonNull()))).endsWith(""""like":null}""")
    }
}
