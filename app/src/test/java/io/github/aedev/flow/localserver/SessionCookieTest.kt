package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionCookieTest {
    @Test
    fun `the cookie is not readable by scripts and not sent cross-site`() {
        val header = SessionCookie.header("abc")

        assertThat(header).startsWith("${SessionCookie.NAME}=abc;")
        assertThat(header).contains("HttpOnly")
        assertThat(header).contains("SameSite=Strict")
        assertThat(header).contains("Path=/")
    }

    @Test
    fun `the token is found among other cookies`() {
        assertThat(SessionCookie.tokenIn("a=1; ${SessionCookie.NAME}=tok; b=2")).isEqualTo("tok")
        assertThat(SessionCookie.tokenIn("${SessionCookie.NAME}=tok")).isEqualTo("tok")
    }

    @Test
    fun `no token is found where none was sent`() {
        assertThat(SessionCookie.tokenIn(null)).isNull()
        assertThat(SessionCookie.tokenIn("a=1")).isNull()
        assertThat(SessionCookie.tokenIn("${SessionCookie.NAME}=")).isNull()
        assertThat(SessionCookie.tokenIn("x${SessionCookie.NAME}=tok")).isNull()
    }
}
