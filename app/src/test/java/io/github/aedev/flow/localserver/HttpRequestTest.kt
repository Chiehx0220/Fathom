package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream

class HttpRequestTest {
    private fun head(text: String) = readRequestHead(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

    @Test
    fun `the head ends at the blank line and the headers are read by lower-case name`() {
        val lines = head("GET /a?b=1 HTTP/1.1\r\nHost: x\r\nRange: bytes=0-9\r\n\r\nbody")!!

        assertThat(lines[0]).isEqualTo("GET /a?b=1 HTTP/1.1")
        assertThat(parseRequestHeaders(lines)).containsExactly("host", "x", "range", "bytes=0-9")
    }

    @Test
    fun `a bare line feed ends the head too`() {
        assertThat(head("GET / HTTP/1.1\nHost: x\n\n")).hasSize(2)
    }

    @Test
    fun `a connection that ends before the blank line has no request`() {
        assertThat(head("GET / HTTP/1.1\r\nHost: x\r\n")).isNull()
        assertThat(head("")).isNull()
    }

    @Test
    fun `a head that never ends is refused`() {
        assertThat(head("GET / HTTP/1.1\r\n" + "X: y\r\n".repeat(10_000))).isNull()
    }

    @Test
    fun `every route is a distinct path that starts with a slash`() {
        assertThat(ROUTES.keys.all { it.startsWith("/") }).isTrue()
        assertThat(ROUTES).containsKey("/hls")
    }
}
