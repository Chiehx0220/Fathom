package io.github.aedev.flow.localserver

import io.github.aedev.flow.localserver.LocalHttpServer.ClientHandler
import org.schabi.newpipe.extractor.stream.Frameset
import java.io.OutputStream
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/*
 * The seek bar's preview thumbnails. YouTube publishes a video's frames as storyboards: sprite
 * sheets with a grid of small frames each. Vidstack reads them from a WebVTT file whose cues point
 * at a region of a sheet (`url#xywh=x,y,w,h`), so this builds that file from the storyboard.
 */

/** The storyboard to draw previews from: the largest one no wider than a seek-bar tooltip. */
internal fun List<Frameset>.bestForSeekBar(): Frameset? {
    val usable = filter { it.urls.isNotEmpty() && it.frameWidth > 0 && it.frameHeight > 0 && it.durationPerFrame > 0 }
    return usable.filter { it.frameWidth <= MAX_PREVIEW_WIDTH }.maxByOrNull { it.frameWidth }
        ?: usable.minByOrNull { it.frameWidth }
}

internal fun Frameset.toWebVtt(): String {
    val perSheet = framesPerPageX * framesPerPageY
    val vtt = StringBuilder("WEBVTT\n\n")
    for (frame in 0 until totalCount) {
        val sheet = frame / perSheet
        if (sheet >= urls.size) break
        val slot = frame % perSheet
        val x = (slot % framesPerPageX) * frameWidth
        val y = (slot / framesPerPageX) * frameHeight
        vtt
            .append(vttTime(frame.toLong() * durationPerFrame))
            .append(" --> ")
            .append(vttTime((frame + 1L) * durationPerFrame))
            .append('\n')
            .append(
                IMAGE_ROUTE,
            ).append(
                URLEncoder.encode(urls[sheet], "UTF-8"),
            ).append("#xywh=")
            .append(x)
            .append(',')
            .append(y)
            .append(',')
            .append(frameWidth)
            .append(',')
            .append(frameHeight)
            .append("\n\n")
    }
    return vtt.toString()
}

private fun vttTime(millis: Long): String =
    String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", millis / 3600000, millis / 60000 % 60, millis / 1000 % 60, millis % 1000)

private const val MAX_PREVIEW_WIDTH = 320

/*
 * Images from a host the browser cannot fetch directly - YouTube's refuses the player's CORS mode
 * (black previews); Bilibili's hotlink-checks the Referer and 403s without it. Both are fixed the
 * same way: fetched here, with whatever header that host needs, and served back same-origin.
 */
private const val IMAGE_ROUTE = "/image-proxy?u="

/** The extra request headers [url]'s host needs to answer, or null when it needs no proxying. */
private fun proxiedImageHeaders(url: String): Map<String, String>? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (uri.scheme != "https") return null
    return when {
        host == "ytimg.com" || host.endsWith(".ytimg.com") -> emptyMap()
        host.endsWith("hdslb.com") -> mapOf("Referer" to "https://www.bilibili.com/", "Origin" to "https://www.bilibili.com")
        else -> null
    }
}

@Throws(Exception::class)
internal fun ClientHandler.handleThumbnailsProxy(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val mediaUrl = requireNotNull(params["id"]) { "Missing 'id' parameter" }
    val frameset = LocalServerSource.streamInfo(dbHelper.appContext, serviceId, mediaUrl).previewFrames.bestForSeekBar()
    if (frameset == null) {
        sendResponse(os, 404, "No preview thumbnails.", "text/plain; charset=UTF-8")
        return
    }
    sendResponse(os, 200, frameset.toWebVtt(), "text/vtt; charset=UTF-8")
}

@Throws(Exception::class)
internal fun ClientHandler.handleImageProxy(
    os: OutputStream,
    params: Map<String, String>,
) {
    val url = params["u"]
    val extraHeaders = url?.let { proxiedImageHeaders(it) }
    if (url == null || extraHeaders == null) {
        sendResponse(os, 400, "Not a proxiable image.", "text/plain; charset=UTF-8")
        return
    }
    val reqBuilder =
        okhttp3.Request
            .Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
    extraHeaders.forEach { (name, value) -> reqBuilder.header(name, value) }
    LocalHttpServer.httpClient.newCall(reqBuilder.build()).execute().use { response ->
        val body = response.body?.bytes() ?: ByteArray(0)
        if (!response.isSuccessful) {
            sendResponse(os, 502, "Image unavailable.", "text/plain; charset=UTF-8")
            return
        }
        val contentType = response.header("Content-Type") ?: "image/jpeg"
        val head =
            listOf(
                "HTTP/1.1 200 OK",
                "Content-Type: $contentType",
                "Content-Length: ${body.size}",
                "Cache-Control: public, max-age=21600",
                "Connection: close",
            ).joinToString(separator = "\r\n", postfix = "\r\n\r\n")
        os.write(head.toByteArray(Charsets.UTF_8))
        os.write(body)
        os.flush()
    }
}

/** A same-origin URL for an image the browser cannot fetch directly; the original URL for any other host. */
internal fun sameOriginImageUrl(url: String): String =
    if (proxiedImageHeaders(url) != null) IMAGE_ROUTE + URLEncoder.encode(url, "UTF-8") else url
