package io.github.aedev.flow.localserver

import io.github.aedev.flow.localserver.LocalHttpServer.ClientHandler
import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.player.error.StreamDenialKind
import io.github.aedev.flow.player.stream.ClientGateTracker
import io.github.aedev.flow.utils.videoIdFromUrl
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.io.OutputStream

// Stream, manifest and subtitle proxying plus static assets, split from LocalHttpServer.kt. Companion members are referenced with the LocalHttpServer. qualifier.

/** Picks the stream URL a `/stream` request wants out of [extractor]'s lists. */
private fun ClientHandler.resolveDirectUrl(
    extractor: StreamLists,
    requestedItag: Int,
    requestedTrackId: String?,
    mediaType: String?,
    params: Map<String, String>,
): String? {
    // Bilibili reports itag=-1 for everything; the manifest tags each Representation with mtype instead.
    if (requestedItag == -1 && mediaType != null) {
        if ("audio" == mediaType) {
            val audioOnly = extractor.audioStreams
            if (audioOnly.isNotEmpty()) {
                var best = audioOnly[0]
                for (candidate in audioOnly) {
                    if (candidate.averageBitrate > best.averageBitrate) {
                        best = candidate
                    }
                }
                return best.content
            }
        } else if ("video" == mediaType) {
            val videoOnly = extractor.videoOnlyStreams.ifEmpty { extractor.videoStreams }
            if (videoOnly.isNotEmpty()) {
                return videoOnly[0].content
            }
        }
        return null
    }

    if (requestedItag != -1) {
        for (stream in extractor.videoStreams) {
            if (stream.itag == requestedItag) return stream.content
        }
        for (stream in extractor.videoOnlyStreams) {
            if (stream.itag == requestedItag) return stream.content
        }
        for (stream in extractor.audioStreams) {
            if (stream.itag == requestedItag) {
                val streamTrackId = stream.audioTrackId ?: ""
                val reqTrackId = requestedTrackId ?: ""
                if (streamTrackId == reqTrackId) return stream.content
            }
        }
    }

    val targetQuality = params["quality"] ?: dbHelper.nativeVideoQuality()
    val targetHeight = LocalHttpServer.getResolutionHeight(targetQuality)

    val progressiveStreams = extractor.videoStreams
    if (progressiveStreams.isNotEmpty()) {
        var selectedStream: VideoStream? = null
        var bestHeight = -1
        for (stream in progressiveStreams) {
            val height = LocalHttpServer.getResolutionHeight(stream.resolution)
            if (height <= targetHeight && height > bestHeight) {
                bestHeight = height
                selectedStream = stream
            }
        }
        if (selectedStream == null) {
            for (stream in progressiveStreams) {
                val height = LocalHttpServer.getResolutionHeight(stream.resolution)
                if (height > bestHeight) {
                    bestHeight = height
                    selectedStream = stream
                }
            }
        }
        return (selectedStream ?: progressiveStreams[0]).content
    }

    try {
        val hlsUrl = extractor.hlsUrl
        if (!hlsUrl.isNullOrEmpty()) return hlsUrl
    } catch (e: Exception) {
    }

    val rawAudioStreams = extractor.audioStreams
    if (rawAudioStreams.isNotEmpty()) {
        // Best track first; "original" is marked by an "(original)" suffix in audioTrackName.
        var audioStreams: MutableList<AudioStream> = ArrayList(rawAudioStreams)
        audioStreams.sortWith(LocalHttpServer.audioTrackPriorityComparator())
        val bestTrackId = audioStreams[0].audioTrackId
        val filteredStreams = audioStreams.filter { it.audioTrackId == bestTrackId }
        if (filteredStreams.isNotEmpty()) {
            audioStreams = filteredStreams.toMutableList()
        }
        return audioStreams[0].content
    }
    return null
}

/** The User-Agent to fetch [directUrl] with: the client that minted it, matched exactly. */
private fun cdnUserAgent(
    directUrl: String,
    cacheKey: String,
): String {
    val default = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    if (!directUrl.contains("googlevideo.com")) return default
    LocalHttpServer.streamUaCache.get(cacheKey)?.let { return it }
    // No InnerTube-sourced User-Agent cached (older cache entry, or extraction fell back to NewPipe) -
    // guess it back out of the URL's own client marker.
    return try {
        when {
            directUrl.contains("c=IOS") || directUrl.contains("c=ios") -> {
                org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
                    .getIosUserAgent(null)
            }

            directUrl.contains("c=VISIONOS") || directUrl.contains("c=visionos") -> {
                org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
                    .getVisionOsUserAgent(null)
            }

            else -> {
                org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
                    .getAndroidUserAgent(null)
            }
        }
    } catch (e: Exception) {
        default
    }
}

/**
 * GVS refused [directUrl] (403/404/410). Reads why from the URL itself, remembers it so the ladder
 * skips a gated/refused client next time, and drops every cache tied to this video so the retry
 * re-extracts instead of handing back the same denied URL.
 */
private fun reportStreamDenial(
    directUrl: String,
    cacheKey: String,
    videoId: String?,
) {
    val kind = StreamDenialClassifier.classify(directUrl)
    val client = StreamDenialClassifier.clientOf(directUrl)
    LocalHttpServer.log("CDN denial kind=$kind client=$client url=$directUrl")
    when (kind) {
        StreamDenialKind.ATTESTATION_GATED -> {
            ClientGateTracker.reportGated(client)
        }

        StreamDenialKind.TOKEN_REJECTED -> {
            ClientGateTracker.reportRefused(client)
        }

        else -> {}
    }
    videoId?.let { LocalServerYouTubeStreams.invalidate(it) }
    LocalHttpServer.streamUrlCache.remove(cacheKey)
    LocalHttpServer.streamUaCache.remove(cacheKey)
}

@Throws(Exception::class)
internal fun ClientHandler.handleStreamProxy(
    os: OutputStream,
    params: Map<String, String>,
    requestHeaders: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]
    val itagParam = params["itag"]

    var rangeHeader: String? = null
    for (key in requestHeaders.keys) {
        if ("range".equals(key, ignoreCase = true)) {
            rangeHeader = requestHeaders[key]
            break
        }
    }

    LocalHttpServer.log("STREAM REQUEST itag=$itagParam range=$rangeHeader id=$mediaUrl")

    var requestedItag = -1
    if (itagParam != null) {
        try {
            requestedItag = itagParam.toInt()
        } catch (e: Exception) {
        }
    }

    val requestedTrackId = params["trackId"]
    // A representation of the manifest is asked for by its own id; itag and mtype are for the other callers (downloads, the plain player URL).
    val repId = params["rep"]
    // mtype is part of the cache key: itag-less services (Bilibili) share itag=-1 between video and audio.
    val mediaType = params["mtype"] ?: repId?.let { if (it.startsWith("a")) "audio" else "video" }
    val resolvedMediaUrl = requireNotNull(mediaUrl) { "Missing 'id' parameter" }
    val videoId = videoIdFromUrl(resolvedMediaUrl)
    val cacheKey =
        if (repId != null) {
            repCacheKey(serviceId, resolvedMediaUrl, repId)
        } else {
            serviceId.toString() + "_" + mediaUrl + "_" + requestedItag +
                (if (requestedTrackId != null) "_$requestedTrackId" else "") +
                (if (mediaType != null) "_$mediaType" else "")
        }

    // LocalServerSource.streams is always asked fresh here: a stream request wants its own extractor
    // fetch, not the one the manifest/watch page's cache is sharing.
    fun resolve(useCache: Boolean): String? {
        if (useCache) LocalHttpServer.streamUrlCache.get(cacheKey)?.let { return it }
        val extractor = LocalServerSource.streams(dbHelper.appContext, serviceId, resolvedMediaUrl, fresh = true)
        val url =
            (
                if (repId !=
                    null
                ) {
                    DashCatalog.urlOf(extractor, repId)
                } else {
                    resolveDirectUrl(extractor, requestedItag, requestedTrackId, mediaType, params)
                }
            )
                ?: return null
        LocalHttpServer.streamUrlCache.put(cacheKey, url, 3600000)
        if (videoId != null) {
            LocalServerYouTubeStreams.extractionFor(videoId)?.usedClient?.userAgent?.let {
                LocalHttpServer.streamUaCache.put(cacheKey, it, 3600000)
            }
        }
        return url
    }

    var directUrl = resolve(useCache = true)
    if (directUrl == null) {
        sendResponse(os, 404, "Stream URL not found.", "text/plain; charset=UTF-8")
        return
    }

    LocalHttpServer.log("Proxying stream from: $directUrl")

    var response = fetchFromCdn(directUrl, cacheKey, rangeHeader)
    if (response.code !in 200..299 && directUrl.contains("googlevideo.com")) {
        LocalHttpServer.log("CDN status=${response.code} on first attempt, re-extracting and retrying once")
        response.close()
        reportStreamDenial(directUrl, cacheKey, videoId)
        // A fresh URL to retry, or - when re-extraction found nothing better - the same one again, so
        // the response forwarded below is always a live, unconsumed one rather than the closed one above.
        directUrl = resolve(useCache = false) ?: directUrl
        LocalHttpServer.log("Retrying stream from: $directUrl")
        response = fetchFromCdn(directUrl, cacheKey, rangeHeader)
    }

    response.use { r ->
        val code = r.code
        LocalHttpServer.log("Incoming Range = $rangeHeader CDN status=$code itag=$requestedItag")

        if (rangeHeader != null && code != 206) {
            LocalHttpServer.log("WARNING: Range requested ($rangeHeader) but CDN returned $code")
        }

        val headBuilder = StringBuilder()
        val statusText = if (code == 206) "Partial Content" else "OK"
        headBuilder
            .append("HTTP/1.1 ")
            .append(code)
            .append(" ")
            .append(statusText)
            .append("\r\n")

        val headersToForward =
            arrayOf(
                "Content-Type",
                "Content-Length",
                "Content-Range",
                "Accept-Ranges",
            )

        for (h in headersToForward) {
            val v = r.header(h)
            if (v != null) {
                headBuilder
                    .append(h)
                    .append(": ")
                    .append(v)
                    .append("\r\n")
            }
        }

        if (r.header("Content-Type") == null) {
            var defaultType = if (requestedItag == 140) "audio/mp4" else "video/mp4"
            if (requestedItag == -1) {
                // Itag-less services: the manifest's mtype decides.
                defaultType =
                    if ("audio" == mediaType) {
                        "audio/mp4"
                    } else if ("video" == mediaType) {
                        "video/mp4"
                    } else {
                        "application/octet-stream"
                    }
            }
            headBuilder.append("Content-Type: ").append(defaultType).append("\r\n")
        }

        if (r.header("Accept-Ranges") == null) {
            headBuilder.append("Accept-Ranges: bytes\r\n")
        }

        headBuilder.append("Access-Control-Allow-Origin: *\r\n")
        headBuilder.append("Access-Control-Allow-Headers: *\r\n")
        headBuilder.append("Access-Control-Expose-Headers: *\r\n")
        headBuilder.append("\r\n")

        if (code == 206) LocalHttpServer.log("Successfully returning 206 Partial Content to client")

        os.write(headBuilder.toString().toByteArray(Charsets.UTF_8))
        os.flush()

        val responseBody = r.body
        if (responseBody != null) {
            try {
                responseBody.byteStream().use { inputStream ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        os.write(buffer, 0, read)
                    }
                }
            } catch (e: IOException) {
                // Client disconnected (pause/seek).
                LocalHttpServer.log("Stream proxy: Client connection closed.")
            }
        }
        os.flush()
    }
}

private fun fetchFromCdn(
    directUrl: String,
    cacheKey: String,
    rangeHeader: String?,
): okhttp3.Response {
    val reqBuilder =
        okhttp3.Request
            .Builder()
            .url(directUrl)
            .header("User-Agent", cdnUserAgent(directUrl, cacheKey))

    // Bilibili's CDN answers 403 without a matching Referer.
    if (directUrl.contains("bilivideo.com") || directUrl.contains("bilibili.com") ||
        directUrl.contains("akamaized.net")
    ) {
        reqBuilder.header("Referer", "https://www.bilibili.com/")
        reqBuilder.header("Origin", "https://www.bilibili.com")
    }

    if (rangeHeader != null) {
        reqBuilder.removeHeader("Range")
        reqBuilder.addHeader("Range", rangeHeader)
        LocalHttpServer.log("Forwarding Range to CDN: $rangeHeader")
    }

    return LocalHttpServer.httpClient.newCall(reqBuilder.build()).execute()
}

@Throws(Exception::class)
internal fun ClientHandler.handleManifestProxy(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]!!

    // Cached: shares its extraction with the watch handlers.
    val extractor = LocalServerSource.streams(dbHelper.appContext, serviceId, mediaUrl, fresh = false)
    // Synchronized: extractor getters are not thread-safe.
    val durationSec = synchronized(extractor) { extractor.length.toDouble() }
    val catalog = DashCatalog.of(extractor, preferredTrack = params["audio_track"], highest = params["prefer"] == "hd")

    // The player asks /stream for each representation by id, and the address it was given is already good for an hour.
    fun warm(repId: String) {
        catalog.urlOf(repId)?.let { LocalHttpServer.streamUrlCache.put(repCacheKey(serviceId, mediaUrl, repId), it, 3600000) }
    }
    catalog.videos.forEach { warm(it.id) }
    catalog.audioTracks.forEach { track -> track.audios.forEach { warm(it.id) } }

    val manifestXml = DashManifest.write(catalog, durationSec) { repId -> DashManifest.streamPath(serviceId, mediaUrl, repId) }
    LocalHttpServer.log("Generated local DASH manifest:\n$manifestXml")

    val bodyBytes = manifestXml.toByteArray(Charsets.UTF_8)

    val responseHeaders =
        "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/dash+xml; charset=UTF-8\r\n" +
            "Content-Length: " + bodyBytes.size + "\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
    os.write(responseHeaders.toByteArray(Charsets.UTF_8))
    os.write(bodyBytes)
    os.flush()
}

private fun repCacheKey(
    serviceId: Int,
    mediaUrl: String,
    repId: String,
) = "${serviceId}_${mediaUrl}_rep_$repId"

@Throws(Exception::class)
internal fun ClientHandler.handleSubtitlesProxy(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]
    val lang = params["lang"]
    val isAuto = "true" == params["auto"]

    val info = LocalServerSource.streamInfo(dbHelper.appContext, serviceId, requireNotNull(mediaUrl) { "Missing 'id' parameter" })

    var targetStream: SubtitlesStream? = null
    var subs: List<SubtitlesStream>? = null
    try {
        subs = info.subtitles
    } catch (e: Exception) {
    }

    if (subs != null) {
        for (sub in subs) {
            if (sub.languageTag == lang && sub.isAutoGenerated == isAuto) {
                targetStream = sub
                break
            }
        }
        if (targetStream == null) {
            for (sub in subs) {
                if (sub.languageTag == lang) {
                    targetStream = sub
                    break
                }
            }
        }
    }

    if (targetStream != null) {
        var subUrl = targetStream.content
        if (subUrl != null) {
            subUrl = subUrl.replace(Regex("&fmt=[^&]*"), "") + "&fmt=vtt"
        }
        val req =
            okhttp3.Request
                .Builder()
                .url(subUrl!!)
                .header("User-Agent", "Mozilla/5.0")
                .build()
        LocalHttpServer.httpClient.newCall(req).execute().use { response ->
            val bodyBytes = (response.body?.string() ?: "").withoutCueSettings().toByteArray(Charsets.UTF_8)
            val contentType = "text/vtt"
            val headers =
                "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + contentType + "; charset=UTF-8\r\n" +
                    "Content-Length: " + bodyBytes.size + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"
            os.write(headers.toByteArray(Charsets.UTF_8))
            os.write(bodyBytes)
            os.flush()
        }
    } else {
        sendResponse(os, 404, "Subtitles not found", "text/plain; charset=UTF-8")
    }
}

/**
 * YouTube's WebVTT puts placement on every cue's timing line ("align:start position:0%"), which the
 * player obeys, pinning captions to the left. Dropping it leaves them centred by the player.
 */
private fun String.withoutCueSettings(): String = replace(Regex("""^(\S+ --> \S+)[ \t]+\S.*$""", RegexOption.MULTILINE), "$1")

// The single-page app: its HTML shell, and its stylesheet and script. The ?v= cache-busting
// parameter is ignored: a content change changes WebAssets.appVersion and so the URL itself,
// making max-age=31536000 immutable safe.
@Throws(Exception::class)
internal fun ClientHandler.handleAppShell(os: OutputStream) {
    sendResponse(os, 200, WebShell.appPage(), "text/html; charset=UTF-8")
}

@Throws(Exception::class)
internal fun ClientHandler.handleAppCss(os: OutputStream) {
    sendResponse(os, 200, WebAssets.appCss, "text/css; charset=UTF-8", "public, max-age=31536000, immutable")
}

@Throws(Exception::class)
internal fun ClientHandler.handleAppJs(os: OutputStream) {
    sendResponse(os, 200, WebAssets.appJs, "application/javascript; charset=UTF-8", "public, max-age=31536000, immutable")
}
