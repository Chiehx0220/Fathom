package io.github.aedev.flow.data.account

internal object GoogleCookie {
    private val REQUIRED = listOf("SAPISID", "__Secure-3PAPISID")

    fun names(cookie: String): List<String> = cookie.split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }

    fun isYouTubeHost(host: String?): Boolean = host != null && (host == "youtube.com" || host.endsWith(".youtube.com"))

    /** SAPISIDHASH is derived from one of these cookies. */
    fun isSignedIn(cookie: String?): Boolean = cookie != null && names(cookie).any { it in REQUIRED }

    /** `DATASYNC_ID` is `<id>||` for the main account, `<delegated>||<main>` for a brand account. */
    fun dataSyncId(raw: String?): String? {
        val value = raw?.trim()?.trim('"')?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        if ("||" !in value) return value
        return if (value.endsWith("||")) value.substringBefore("||") else value.substringAfter("||")
    }
}
