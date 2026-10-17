package io.github.aedev.flow.localserver

/** Session cookie of a paired browser. */
internal object SessionCookie {
    const val NAME = "fathom_session"
    private const val MAX_AGE_S = 30 * 24 * 3600

    fun header(token: String): String = "$NAME=$token; Path=/; Max-Age=$MAX_AGE_S; HttpOnly; SameSite=Strict"

    fun tokenIn(cookieHeader: String?): String? =
        cookieHeader
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$NAME=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotEmpty() }
}
