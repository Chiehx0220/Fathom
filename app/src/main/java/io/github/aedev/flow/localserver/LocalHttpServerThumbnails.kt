package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.stream.Frameset
import java.io.OutputStream
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/*
 * Seek-bar preview thumbnails. YouTube storyboards: sprite sheets, grid of frames. Web player reads WebVTT with cues at sheet regions
 * (`url#xywh=x,y,w,h`); built here from the storyboard.
 */

/** Storyboard for previews: largest no wider than a seek-bar tooltip. */
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
 * Images from hosts the browser cannot fetch directly: YouTube refuses the player's CORS mode (black previews);
 * Bilibili hotlink-checks Referer (403). Fetched here with the host's header, served same-origin.
 */
private const val IMAGE_ROUTE = "/image-proxy?u="

/** Extra request headers [url]'s host needs; null if no proxying needed. */
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
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"] ?: throw BadRequestException("Missing 'id' parameter")
    val frameset = LocalServerSource.streamInfo(dbHelper.appContext, serviceId, mediaUrl).previewFrames.bestForSeekBar()
    if (frameset == null) {
        reply.send(404, "No preview thumbnails.", "text/plain; charset=UTF-8")
        return
    }
    reply.send(200, frameset.toWebVtt(), "text/vtt; charset=UTF-8")
}

@Throws(Exception::class)
internal fun ClientHandler.handleImageProxy(
    reply: Reply,
    params: Map<String, String>,
) {
    val url = params["u"]
    val extraHeaders = url?.let { proxiedImageHeaders(it) }
    if (url == null || extraHeaders == null) {
        reply.send(400, "Not a proxiable image.", "text/plain; charset=UTF-8")
        return
    }
    val reqBuilder =
        okhttp3.Request
            .Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
    extraHeaders.forEach { (name, value) -> reqBuilder.header(name, value) }
    LocalServerCaches.httpClient.newCall(reqBuilder.build()).execute().use { response ->
        val body = response.body?.bytes() ?: ByteArray(0)
        if (!response.isSuccessful) {
            reply.send(502, "Image unavailable.", "text/plain; charset=UTF-8")
            return
        }
        val contentType = response.header("Content-Type") ?: "image/jpeg"
        reply.sendBytes(200, body, contentType, mapOf("Cache-Control" to "public, max-age=21600"))
    }
}

/** Same-origin URL for an image the browser cannot fetch; original URL for other hosts. */
internal fun sameOriginImageUrl(url: String): String =
    if (proxiedImageHeaders(url) != null) IMAGE_ROUTE + URLEncoder.encode(url, "UTF-8") else url
