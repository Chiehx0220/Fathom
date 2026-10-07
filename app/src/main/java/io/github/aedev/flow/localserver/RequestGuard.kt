package io.github.aedev.flow.localserver

import java.util.Locale

/*
 * What the local server checks before it runs a request. The server has no sign-in, so these only keep out
 * what a browser can be tricked into sending: a web page on another site asking this device to change
 * something, and a domain an attacker re-points at this device's address. A program on the same network can
 * still set any header it likes; keeping it out would take a pairing step this does not add.
 */

/** A request missing something it needs; answered with 400 and the message instead of a server error. */
internal class BadRequestException(
    message: String,
) : IllegalArgumentException(message)

/** Routes that change something on the device, or take the queue of commands for the remote page. */
internal val MUTATING_PATHS: Set<String> =
    setOf(
        "/send-link",
        "/play",
        "/send-command",
        "/poll-commands",
        "/remote-state",
        "/release-lock",
        "/history_action",
        "/search-history",
        "/subscribe",
        "/block_channel",
        "/bookmark_playlist",
        "/watch_later_action",
        "/rate_video",
        "/api/player/play",
        "/api/player/pause",
        "/api/player/resume",
        "/api/player/stop",
        "/api/v1/download",
        "/api/v1/watch_progress",
        "/api/v1/settings",
    )

/** Read-only media routes that other devices and players fetch, so they answer cross-origin requests. */
internal val CROSS_ORIGIN_PATHS: Set<String> =
    setOf("/stream", "/hlsvod", "/hls", "/subtitles", "/thumbnails", "/image-proxy", "/danmaku", "/live_chat")

private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal")

/**
 * Whether [hostHeader] names this device by address or by a local name. A page on a public domain that
 * has been pointed at this device's address sends that domain here, and the browser then treats the
 * device as that page's own origin; refusing such a name closes that way in. No header at all comes from
 * a program, not a browser.
 */
internal fun isLocalHostHeader(hostHeader: String?): Boolean {
    val value = hostHeader?.trim().orEmpty()
    if (value.isEmpty()) return hostHeader == null
    val host =
        if (value.startsWith("[")) {
            return value.contains("]")
        } else {
            value.substringBefore(':').lowercase(Locale.US)
        }
    return host == "localhost" ||
        IPV4.matches(host) ||
        !host.contains('.') ||
        LOCAL_SUFFIXES.any { host.endsWith(it) }
}

/**
 * Whether a request that changes something came from one of this server's own pages, or from no browser.
 * A browser says where a request came from in `Sec-Fetch-Site`, or failing that `Origin`; a page cannot
 * change either. `none` is the viewer typing the address themselves.
 */
internal fun isSameOriginRequest(headers: Map<String, String>): Boolean {
    val site = headers["sec-fetch-site"]
    if (site != null) return site == "same-origin" || site == "none"
    val origin = headers["origin"] ?: return true
    val host = headers["host"] ?: return false
    return origin.removePrefix("http://") == host && origin.startsWith("http://")
}

/** Why [path] must not be served to this request, or null when it may be. */
internal fun refusalFor(
    method: String,
    path: String,
    headers: Map<String, String>,
): String? =
    when {
        !isLocalHostHeader(headers["host"]) -> "Forbidden: this address is not recognised"

        method.equals(
            "OPTIONS",
            ignoreCase = true,
        ) && path !in CROSS_ORIGIN_PATHS -> "Forbidden: cross-origin requests are not accepted here"

        path in MUTATING_PATHS && !isSameOriginRequest(headers) -> "Forbidden: this request did not come from the server's own page"

        else -> null
    }

/** The reason phrase of [code] in a status line. */
internal fun statusText(code: Int): String =
    when (code) {
        200 -> "OK"
        204 -> "No Content"
        206 -> "Partial Content"
        302 -> "Found"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        else -> "Internal Server Error"
    }
