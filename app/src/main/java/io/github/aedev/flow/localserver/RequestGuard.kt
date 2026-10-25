package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.Page
import java.util.Locale

/*
 * Header-level request checks: cross-site state change, DNS rebinding (re-pointed domain), foreign WebSocket origin.
 * A program on the network can set any header; that is what [Pairing] is for.
 */

/** Request missing a required part; 400 + message, not server error. */
internal class BadRequestException(
    message: String,
) : IllegalArgumentException(message)

/** Query string, last value wins; unparsable number = absent. */
internal class Query(
    private val values: Map<String, String>,
) {
    operator fun get(name: String): String? = values[name]

    /** Non-empty param [name], else 400. */
    fun required(name: String): String = values[name]?.takeIf { it.isNotEmpty() } ?: throw BadRequestException("Missing '$name' parameter")

    fun page(name: String): Page? = PageToken.decode(values[name])

    fun int(name: String): Int? = values[name]?.toIntOrNull()

    fun long(name: String): Long? = values[name]?.toLongOrNull()

    fun double(name: String): Double? = values[name]?.toDoubleOrNull()

    fun float(name: String): Float? = values[name]?.toFloatOrNull()

    /** `name=1`. */
    fun flag(name: String): Boolean = values[name] == "1"

    /** `name=true` / any other value → false; absent → null. */
    fun bool(name: String): Boolean? = values[name]?.let { it == "true" }
}

/** Routes changing device state. */
internal val MUTATING_PATHS: Set<String> =
    setOf(
        PAIR_PATH,
        "/send-link",
        "/handoff",
        "/api/v1/cast/start",
        "/api/v1/cast/stop",
        "/play",
        "/remote-state",
        "/release-lock",
        "/history_action",
        "/search-history",
        "/subscribe",
        "/block_channel",
        "/bookmark_playlist",
        "/watch_later_action",
        "/playlist_action",
        "/rate_video",
        "/api/v1/download",
        "/api/v1/watch_progress",
        "/api/v1/settings",
    )

/** Saved downloads: same-origin pages only. */
internal val OWN_PAGE_ONLY_PATHS: Set<String> = setOf("/downloadfile")

/** Read-only media routes fetched by other devices/players: cross-origin allowed. */
internal val CROSS_ORIGIN_PATHS: Set<String> =
    setOf("/stream", "/hlsvod", "/hls", "/subtitles", "/thumbnails", "/image-proxy", "/danmaku", "/live_chat")

private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal")

/**
 * [hostHeader] names this device by address or local name. Public domain re-pointed at this address sends that domain; browser then treats the device as that page's origin (DNS rebinding):
 * refused. No header = program, not browser.
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

/** Same-origin by `Sec-Fetch-Site`, else `Origin`; `none` is a typed address. No such header means a program. */
internal fun isSameOriginRequest(headers: Map<String, String>): Boolean {
    val site = headers["sec-fetch-site"]
    if (site != null) return site == "same-origin" || site == "none"
    val origin = headers["origin"] ?: return true
    val host = headers["host"] ?: return false
    return origin.removePrefix("http://") == host && origin.startsWith("http://")
}

/** Why [path] must not be served to this request; null if allowed. */
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

        path in OWN_PAGE_ONLY_PATHS && !isSameOriginRequest(headers) -> "Forbidden: this file is only served to the server's own page"

        else -> null
    }

private fun hostOf(authority: String): String =
    (if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')).lowercase(Locale.US)

/** Browser WebSockets must come from a page of this server's own address; no `Origin` means a program. */
internal fun isAllowedWebSocketOrigin(
    origin: String?,
    hostHeader: String?,
): Boolean {
    if (origin == null) return true
    if (hostHeader == null || !isLocalHostHeader(hostHeader) || !origin.startsWith("http://")) return false
    return hostOf(origin.removePrefix("http://")) == hostOf(hostHeader)
}

/** Reason phrase of [code]. */
internal fun statusText(code: Int): String =
    when (code) {
        200 -> "OK"
        204 -> "No Content"
        206 -> "Partial Content"
        302 -> "Found"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        416 -> "Range Not Satisfiable"
        502 -> "Bad Gateway"
        else -> "Internal Server Error"
    }
