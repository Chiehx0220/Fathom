package org.schabi.newpipe.localserver

import java.io.OutputStream
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.localserver.LocalHttpServer.ClientHandler

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
        vtt.append(vttTime(frame.toLong() * durationPerFrame)).append(" --> ").append(vttTime((frame + 1L) * durationPerFrame)).append('\n')
            .append(SHEET_ROUTE).append(URLEncoder.encode(urls[sheet], "UTF-8")).append("#xywh=").append(x).append(',').append(y).append(',').append(frameWidth).append(',').append(frameHeight)
            .append("\n\n")
    }
    return vtt.toString()
}

private fun vttTime(millis: Long): String =
    String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", millis / 3600000, millis / 60000 % 60, millis / 1000 % 60, millis % 1000)

private const val MAX_PREVIEW_WIDTH = 320

/*
 * The player asks for these images in CORS mode, and YouTube's image host does not allow that, so the
 * browser would refuse them (black previews). Fetching them here makes them same-origin.
 */
private const val SHEET_ROUTE = "/thumbnail-sheet?u="

private fun isYouTubeImageUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme == "https" && (host == "ytimg.com" || host.endsWith(".ytimg.com"))
}

@Throws(Exception::class)
internal fun ClientHandler.handleThumbnailsProxy(os: OutputStream, params: Map<String, String>) {
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
internal fun ClientHandler.handleThumbnailSheetProxy(os: OutputStream, params: Map<String, String>) {
    val url = params["u"]
    if (url == null || !isYouTubeImageUrl(url)) {
        sendResponse(os, 400, "Not a thumbnail sheet.", "text/plain; charset=UTF-8")
        return
    }
    val request = okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
    LocalHttpServer.httpClient.newCall(request).execute().use { response ->
        val body = response.body?.bytes() ?: ByteArray(0)
        if (!response.isSuccessful) {
            sendResponse(os, 502, "Thumbnail sheet unavailable.", "text/plain; charset=UTF-8")
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

/** A same-origin URL for a YouTube image the player draws in CORS mode (poster, artwork); null for other hosts. */
internal fun sameOriginImageUrl(url: String): String? =
    if (isYouTubeImageUrl(url)) SHEET_ROUTE + URLEncoder.encode(url, "UTF-8") else null
