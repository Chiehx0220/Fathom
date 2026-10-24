package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliCdn
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.net.URLEncoder

// Bilibili live rooms for the web page: HLS relay (CDN needs a Referer browsers cannot send) + chat poll.

private const val PLAYLIST_TYPE = "application/vnd.apple.mpegurl"
private val URI_ATTRIBUTE = Regex("""URI="([^"]*)"""")

/** Relay path for [url] (playlist/segment fetched through this server). */
internal fun hlsRelayPath(url: String): String = "/hls?u=" + URLEncoder.encode(url, "UTF-8")

/**
 * Playlist with all addresses pointed at the relay, absolutized against [base].
 * Content-steering tag dropped (steering server reached without Referer).
 */
internal fun rewriteHlsPlaylist(
    playlist: String,
    base: String,
): String {
    fun relay(reference: String) = hlsRelayPath(URI(base).resolve(reference).toString())
    return playlist
        .lineSequence()
        .filterNot { it.startsWith("#EXT-X-CONTENT-STEERING") }
        .joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                line.startsWith("#") -> URI_ATTRIBUTE.replace(line) { "URI=\"" + relay(it.groupValues[1]) + "\"" }
                else -> relay(line.trim())
            }
        }
}

/**
 * Origin of a live room's master playlist. Segment hosts = stream CDN nodes only; this one path of one Bilibili host allowed alongside.
 */
internal fun isLiveGateway(url: String): Boolean {
    val parsed = url.toHttpUrlOrNull() ?: return false
    return parsed.scheme == "https" && parsed.host == "api.live.bilibili.com" && parsed.encodedPath.startsWith("/xlive/play-gateway/")
}

/** Relays one HLS playlist/segment of a Bilibili CDN or live gateway; other addresses refused (not an open proxy). */
@Throws(Exception::class)
internal fun ClientHandler.handleHlsProxy(
    reply: Reply,
    params: Map<String, String>,
) {
    val url = params["u"]
    if (url.isNullOrEmpty() || !url.startsWith("https://") || !(BilibiliCdn.isCdnUrl(url) || isLiveGateway(url))) {
        reply.send(404, "Not a Bilibili live address.", "text/plain; charset=UTF-8")
        return
    }
    val request =
        okhttp3.Request
            .Builder()
            .url(url)
            .apply { BilibiliCdn.headers().forEach { (name, value) -> header(name, value) } }
            .build()
    LocalServerCaches.httpClient.newCall(request).execute().use { response ->
        val body = response.body
        if (!response.isSuccessful) {
            reply.send(502, "The live server answered ${response.code}.", "text/plain; charset=UTF-8")
            return
        }
        val type = response.header("Content-Type").orEmpty()
        val isPlaylist = url.substringBefore('?').endsWith(".m3u8") || type.contains("mpegurl", ignoreCase = true)
        if (isPlaylist) {
            val text = rewriteHlsPlaylist(body.string(), response.request.url.toString())
            reply.sendBytes(
                200,
                text.toByteArray(Charsets.UTF_8),
                PLAYLIST_TYPE,
                mapOf("Cache-Control" to "no-store", "Access-Control-Allow-Origin" to "*"),
            )
        } else {
            reply.stream(
                200,
                type.ifEmpty { "video/mp4" },
                body.contentLength().takeIf { it >= 0 },
                mapOf("Access-Control-Allow-Origin" to "*"),
            ) { out ->
                try {
                    body.byteStream().copyTo(out)
                } catch (e: IOException) {
                    // Player closed the connection (moved to newer segment).
                }
            }
        }
    }
}

/** Room chat since last poll's position; first poll (no position) only learns it. */
@Throws(Exception::class)
internal fun ClientHandler.handleLiveChat(
    reply: Reply,
    params: Map<String, String>,
) {
    val roomId = params["room"]?.toLongOrNull() ?: throw BadRequestException("Missing 'room' parameter")
    val (seq, items) = LocalServerBilibiliLive.chatSince(dbHelper.appContext, roomId, params["since"]?.toLongOrNull())
    reply.sendJson(JSONObject().put("seq", seq).put("items", items))
}
