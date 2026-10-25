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
        val (body, type) = failureBody(json = true, 400, "Missing 'id' parameter")
        assertThat(type).isEqualTo("application/json")
        val json = JSONObject(body)
        assertThat(json.getString("error")).isEqualTo("Missing 'id' parameter")
        assertThat(json.getString("status")).isEqualTo("error")
    }

    @Test
    fun `json routes outside api answer json too`() {
        assertThat(failureBody(json = true, 400, "Unknown action").second).isEqualTo("application/json")
    }

    @Test
    fun `media routes stay plain text and never leak a 500 message`() {
        assertThat(
            failureBody(json = false, 400, "Missing 'id' parameter"),
        ).isEqualTo("Bad Request: Missing 'id' parameter" to "text/plain; charset=UTF-8")
        assertThat(failureBody(json = false, 500, "socket /data/x")).isEqualTo("Internal Server Error" to "text/plain; charset=UTF-8")
    }

    @Test
    fun `a missing or empty parameter is a bad request`() {
        assertThat(Query(mapOf("id" to "x")).required("id")).isEqualTo("x")
        assertThrows(BadRequestException::class.java) { Query(emptyMap()).required("id") }
        assertThrows(BadRequestException::class.java) { Query(mapOf("id" to "")).required("id") }
    }
}
