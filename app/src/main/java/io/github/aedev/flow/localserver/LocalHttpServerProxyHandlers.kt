package io.github.aedev.flow.localserver

import io.github.aedev.flow.utils.youtubeVideoIdFromUrl
import okhttp3.Response
import java.io.IOException

// Stream proxy. Helpers: StreamPick, CdnFetch, LocalServerMedia, LocalServerCaches.

private const val STREAM_URL_TTL_MS = 3_600_000L
private const val COPY_BUFFER_BYTES = 65_536
private const val ITAG_AAC_128 = 140

internal fun RequestContext.handleStreamProxy(
    reply: Reply,
    params: Query,
    requestHeaders: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]
    val itagParam = params["itag"]
    val rangeHeader = requestHeaders.entries.firstOrNull { "range".equals(it.key, ignoreCase = true) }?.value
    serverLog("STREAM REQUEST itag=$itagParam range=$rangeHeader id=$mediaUrl")

    val itag = itagParam?.toIntOrNull() ?: NO_ITAG
    val trackId = params["trackId"]
    // Manifest Representation by id; itag/mtype for downloads, plain player URL.
    val repId = params["rep"]
    // mtype in cache key: itag-less services (Bilibili) share itag=-1 for video and audio.
    val mediaType = params["mtype"] ?: repId?.let { if (it.startsWith("a")) "audio" else "video" }
    val resolvedMediaUrl = mediaUrl ?: throw BadRequestException("Missing 'id' parameter")
    val videoId = youtubeVideoIdFromUrl(resolvedMediaUrl)
    val cacheKey =
        if (repId != null) {
            repCacheKey(serviceId, resolvedMediaUrl, repId)
        } else {
            "${serviceId}_${mediaUrl}_$itag" + (trackId?.let { "_$it" } ?: "") + (mediaType?.let { "_$it" } ?: "")
        }

    // Fresh extraction, not the manifest/watch cache's.
    fun resolve(useCache: Boolean): String? {
        if (useCache) LocalServerCaches.streamUrlCache.get(cacheKey)?.let { return it }
        val lists = LocalServerSource.streams(dbHelper.appContext, serviceId, resolvedMediaUrl, fresh = true)
        val url =
            (
                if (repId != null) {
                    StreamCatalog.urlOf(lists, repId)
                } else {
                    pickStreamUrl(lists, itag, trackId, mediaType) { params["quality"] ?: dbHelper.videoQuality() }
                }
            ) ?: return null
        LocalServerCaches.streamUrlCache.put(cacheKey, url, STREAM_URL_TTL_MS)
        if (videoId != null) {
            LocalServerYouTubeStreams.extractionFor(videoId)?.usedClient?.userAgent?.let {
                LocalServerCaches.streamUaCache.put(cacheKey, it, STREAM_URL_TTL_MS)
            }
        }
        return url
    }

    val firstUrl = resolve(useCache = true)
    if (firstUrl == null) {
        reply.send(404, "Stream URL not found.", "text/plain; charset=UTF-8")
        return
    }
    serverLog("Proxying stream from: $firstUrl")

    var directUrl = LocalServerYouTubeStreams.swapped(firstUrl)
    var response = fetchFromCdn(directUrl, cacheKey, rangeHeader)
    if (response.code !in 200..299 && "googlevideo.com" in directUrl) {
        serverLog("CDN status=${response.code} on first attempt, retrying once")
        response.close()
        directUrl =
            if (LocalServerYouTubeStreams.swapRefused(directUrl)) {
                LocalServerYouTubeStreams.swapped(directUrl)
            } else {
                dropAfterDenial(directUrl, cacheKey, videoId)
                // Fresh URL, else same one; response always live.
                resolve(useCache = false) ?: directUrl
            }
        serverLog("Retrying stream from: $directUrl")
        response = fetchFromCdn(directUrl, cacheKey, rangeHeader)
    }

    response.use { relay(reply, it, itag, mediaType, rangeHeader) }
}

private fun relay(
    reply: Reply,
    cdn: Response,
    itag: Int,
    mediaType: String?,
    rangeHeader: String?,
) {
    val code = cdn.code
    serverLog("Incoming Range = $rangeHeader CDN status=$code itag=$itag")
    if (rangeHeader != null && code != 206) serverLog("WARNING: Range requested ($rangeHeader) but CDN returned $code")

    val type =
        cdn.header("Content-Type") ?: when {
            // Itag-less services: manifest mtype decides.
            itag == NO_ITAG -> {
                when (mediaType) {
                    "audio" -> "audio/mp4"
                    "video" -> "video/mp4"
                    else -> "application/octet-stream"
                }
            }

            itag == ITAG_AAC_128 -> {
                "audio/mp4"
            }

            else -> {
                "video/mp4"
            }
        }
    val headers =
        buildMap {
            cdn.header("Content-Range")?.let { put("Content-Range", it) }
            put("Accept-Ranges", cdn.header("Accept-Ranges") ?: "bytes")
            put("Access-Control-Allow-Origin", "*")
            put("Access-Control-Allow-Headers", "*")
            put("Access-Control-Expose-Headers", "*")
        }
    if (code == 206) serverLog("Successfully returning 206 Partial Content to client")

    reply.stream(code, type, cdn.header("Content-Length")?.toLongOrNull(), headers) { out ->
        try {
            cdn.body?.byteStream()?.copyTo(out, COPY_BUFFER_BYTES)
        } catch (e: IOException) {
            // Client disconnected (pause/seek).
            serverLog("Stream proxy: Client connection closed.")
        }
    }
}
