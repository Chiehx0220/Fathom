package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RequestGuardTest {
    private fun headers(vararg pairs: Pair<String, String>) = mapOf("host" to "192.168.1.5:8080") + pairs

    @Test
    fun `a device is reached by address or local name`() {
        for (host in listOf(
            "192.168.1.5:8080",
            "10.0.0.2",
            "localhost:8080",
            "[::1]:8080",
            "pixel",
            "pixel.local:8080",
            "tv.lan",
            "x.home.arpa",
        )) {
            assertThat(isLocalHostHeader(host)).isTrue()
        }
    }

    @Test
    fun `a public domain pointed at the device is refused`() {
        for (host in listOf("evil.example:8080", "rebind.attacker.com", "192.168.1.5.evil.example", "")) {
            assertThat(isLocalHostHeader(host)).isFalse()
        }
    }

    @Test
    fun `a request with no host header comes from a program and is let through`() {
        assertThat(isLocalHostHeader(null)).isTrue()
    }

    @Test
    fun `a page of the server's own origin may change things`() {
        assertThat(isSameOriginRequest(headers("sec-fetch-site" to "same-origin"))).isTrue()
        assertThat(isSameOriginRequest(headers("sec-fetch-site" to "none"))).isTrue()
        assertThat(isSameOriginRequest(headers("origin" to "http://192.168.1.5:8080"))).isTrue()
    }

    @Test
    fun `a page of another origin may not, whatever it says`() {
        assertThat(isSameOriginRequest(headers("sec-fetch-site" to "cross-site"))).isFalse()
        assertThat(isSameOriginRequest(headers("sec-fetch-site" to "same-site"))).isFalse()
        assertThat(isSameOriginRequest(headers("origin" to "https://evil.example"))).isFalse()
        assertThat(isSameOriginRequest(headers("origin" to "http://192.168.1.5:8081"))).isFalse()
        assertThat(isSameOriginRequest(headers("origin" to "null"))).isFalse()
        assertThat(isSameOriginRequest(headers("sec-fetch-site" to "same-origin", "origin" to "https://evil.example"))).isTrue()
    }

    @Test
    fun `a program that sends neither header is let through`() {
        assertThat(isSameOriginRequest(headers())).isTrue()
    }

    @Test
    fun `changing routes refuse a cross-site page, reading routes do not`() {
        val crossSite = headers("sec-fetch-site" to "cross-site")

        assertThat(refusalFor("GET", "/history_action", crossSite)).isNotNull()
        assertThat(refusalFor("GET", "/subscribe", crossSite)).isNotNull()
        assertThat(refusalFor("GET", "/api/v1/download", crossSite)).isNotNull()
        assertThat(refusalFor("GET", "/api/v1/home", crossSite)).isNull()
        assertThat(refusalFor("GET", "/", crossSite)).isNull()
        assertThat(refusalFor("GET", "/history_action", headers("sec-fetch-site" to "same-origin"))).isNull()
    }

    @Test
    fun `a request for a rebinding host is refused on every route`() {
        val rebound = mapOf("host" to "evil.example:8080", "sec-fetch-site" to "same-origin")

        assertThat(refusalFor("GET", "/api/v1/home", rebound)).isNotNull()
        assertThat(refusalFor("GET", "/", rebound)).isNotNull()
    }

    @Test
    fun `only the media routes answer a cross-origin preflight`() {
        assertThat(refusalFor("OPTIONS", "/stream", headers())).isNull()
        assertThat(refusalFor("OPTIONS", "/hls", headers())).isNull()
        assertThat(refusalFor("OPTIONS", "/history_action", headers())).isNotNull()
        assertThat(refusalFor("OPTIONS", "/api/v1/ping", headers())).isNotNull()
    }

    @Test
    fun `status lines carry the right reason phrase`() {
        assertThat(statusText(200)).isEqualTo("OK")
        assertThat(statusText(204)).isEqualTo("No Content")
        assertThat(statusText(400)).isEqualTo("Bad Request")
        assertThat(statusText(403)).isEqualTo("Forbidden")
        assertThat(statusText(404)).isEqualTo("Not Found")
        assertThat(statusText(500)).isEqualTo("Internal Server Error")
    }
}
