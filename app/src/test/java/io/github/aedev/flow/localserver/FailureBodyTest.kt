package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class FailureBodyTest {
    @Test
    fun `api failures are json carrying the message`() {
        val (body, type) = failureBody("/api/v1/video", 400, "Missing 'id' parameter")
        assertThat(type).isEqualTo("application/json")
        val json = JSONObject(body)
        assertThat(json.getString("error")).isEqualTo("Missing 'id' parameter")
        assertThat(json.getString("status")).isEqualTo("error")
    }

    @Test
    fun `json routes outside api answer json too`() {
        assertThat(failureBody("/subscribe", 400, "Unknown action").second).isEqualTo("application/json")
    }

    @Test
    fun `media routes stay plain text and never leak a 500 message`() {
        assertThat(
            failureBody("/stream", 400, "Missing 'id' parameter"),
        ).isEqualTo("Bad Request: Missing 'id' parameter" to "text/plain; charset=UTF-8")
        assertThat(failureBody("/stream", 500, "socket /data/x")).isEqualTo("Internal Server Error" to "text/plain; charset=UTF-8")
    }

    @Test
    fun `every json route is served`() {
        assertThat(ROUTES.keys).containsAtLeastElementsIn(JSON_ROUTES)
    }

    @Test
    fun `a missing or empty parameter is a bad request`() {
        assertThat(mapOf("id" to "x").required("id")).isEqualTo("x")
        assertThrows(BadRequestException::class.java) { emptyMap<String, String>().required("id") }
        assertThrows(BadRequestException::class.java) { mapOf("id" to "").required("id") }
    }
}
