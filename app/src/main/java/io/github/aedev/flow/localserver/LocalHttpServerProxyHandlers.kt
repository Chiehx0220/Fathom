package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliCdn
import io.github.aedev.flow.player.datasource.GoogleVideoRequestPolicy
import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.utils.youtubeVideoIdFromUrl
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException

// Stream, manifest, subtitle proxies. Helpers: LocalServerMedia, LocalServerCaches, RemoteSession.

/** Stream URL a `/stream` request wants from [extractor]'s lists. */
private fun RequestContext.resolveDirectUrl(
    extractor: StreamLists,
    requestedItag: Int,
    requestedTrackId: String?,
    mediaType: String?,
    params: Query,
): String? {
    // Bilibili itag=-1 for all; manifest tags each Representation with mtype.
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

    val targetQuality = params["quality"] ?: dbHelper.videoQuality()
    val targetHeight = LocalServerMedia.getResolutionHeight(targetQuality)

    val progressiveStreams = extractor.videoStreams
    if (progressiveStreams.isNotEmpty()) {
        var selectedStream: VideoStream? = null
        var bestHeight = -1
        for (stream in progressiveStreams) {
            val height = LocalServerMedia.getResolutionHeight(stream.resolution)
            if (height <= targetHeight && height > bestHeight) {
                bestHeight = height
                selectedStream = stream
            }
        }
        if (selectedStream == null) {
            for (stream in progressiveStreams) {
                val height = LocalServerMedia.getResolutionHeight(stream.resolution)
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
        // Best track first; "original" = "(original)" suffix in audioTrackName.
        var audioStreams: MutableList<AudioStream> = ArrayList(rawAudioStreams)
        audioStreams.sortWith(LocalServerMedia.audioTrackPriorityComparator())
        val bestTrackId = audioStreams[0].audioTrackId
        val filteredStreams = audioStreams.filter { it.audioTrackId == bestTrackId }
        if (filteredStreams.isNotEmpty()) {
            audioStreams = filteredStreams.toMutableList()
        }
        return audioStreams[0].content
    }
    return null
}

/** User-Agent for [directUrl]: minting client, exact match. */
private fun cdnUserAgent(
    directUrl: String,
    cacheKey: String,
): String {
    val default = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    if (!directUrl.contains("googlevideo.com")) return default
    return GoogleVideoRequestPolicy.userAgent(
        StreamDenialClassifier.clientOf(directUrl),
        cachedOrGuessedUserAgent(directUrl, cacheKey, default),
    )
}

private fun cachedOrGuessedUserAgent(
    directUrl: String,
    cacheKey: String,
    default: String,
): String {
    LocalServerCaches.streamUaCache.get(cacheKey)?.let { return it }
    // No cached InnerTube User-Agent (older entry or NewPipe fallback): guess from URL's client marker.
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
 * GVS refused [directUrl], no same-file replacement recorded: web-client video → TV_TIZEN, video's caches dropped; retry re-extracts.
 */
private fun dropAfterDenial(
    directUrl: String,
    cacheKey: String,
    videoId: String?,
) {
    val client = StreamDenialClassifier.clientOf(directUrl)
    serverLog("CDN denial kind=${StreamDenialClassifier.classify(directUrl)} client=$client url=$directUrl")
    videoId?.let {
        if (client?.uppercase() in LocalServerYouTubeStreams.WEB_CLIENTS) {
            LocalServerYouTubeStreams.markWalled(it)
        } else {
            LocalServerYouTubeStreams.invalidate(it)
        }
    }
    LocalServerCaches.streamUrlCache.remove(cacheKey)
    LocalServerCaches.streamUaCache.remove(cacheKey)
}

internal fun RequestContext.handleStreamProxy(
    reply: Reply,
    params: Query,
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

    serverLog("STREAM REQUEST itag=$itagParam range=$rangeHeader id=$mediaUrl")

    var requestedItag = -1
    if (itagParam != null) {
        try {
            requestedItag = itagParam.toInt()
        } catch (e: Exception) {
        }
    }

    val requestedTrackId = params["trackId"]
    // Manifest Representation asked by own id; itag/mtype for other callers (downloads, plain player URL).
    val repId = params["rep"]
    // mtype in cache key: itag-less services (Bilibili) share itag=-1 for video and audio.
    val mediaType = params["mtype"] ?: repId?.let { if (it.startsWith("a")) "audio" else "video" }
    val resolvedMediaUrl = mediaUrl ?: throw BadRequestException("Missing 'id' parameter")
    val videoId = youtubeVideoIdFromUrl(resolvedMediaUrl)
    val cacheKey =
        if (repId != null) {
            repCacheKey(serviceId, resolvedMediaUrl, repId)
        } else {
            serviceId.toString() + "_" + mediaUrl + "_" + requestedItag +
                (if (requestedTrackId != null) "_$requestedTrackId" else "") +
                (if (mediaType != null) "_$mediaType" else "")
        }

    // LocalServerSource.streams always fresh: stream request needs own extractor fetch, not the manifest/watch cache's.
    fun resolve(useCache: Boolean): String? {
        if (useCache) LocalServerCaches.streamUrlCache.get(cacheKey)?.let { return it }
        val extractor = LocalServerSource.streams(dbHelper.appContext, serviceId, resolvedMediaUrl, fresh = true)
        val url =
            (
                if (repId !=
                    null
                ) {
                    StreamCatalog.urlOf(extractor, repId)
                } else {
                    resolveDirectUrl(extractor, requestedItag, requestedTrackId, mediaType, params)
                }
            )
                ?: return null
        LocalServerCaches.streamUrlCache.put(cacheKey, url, 3600000)
        if (videoId != null) {
            LocalServerYouTubeStreams.extractionFor(videoId)?.usedClient?.userAgent?.let {
                LocalServerCaches.streamUaCache.put(cacheKey, it, 3600000)
            }
        }
        return url
    }

    var directUrl = resolve(useCache = true)
    if (directUrl == null) {
        reply.send(404, "Stream URL not found.", "text/plain; charset=UTF-8")
        return
    }

    serverLog("Proxying stream from: $directUrl")

    directUrl = LocalServerYouTubeStreams.swapped(directUrl)
    var response = fetchFromCdn(directUrl, cacheKey, rangeHeader)
    if (response.code !in 200..299 && directUrl.contains("googlevideo.com")) {
        serverLog("CDN status=${response.code} on first attempt, retrying once")
        response.close()
        if (LocalServerYouTubeStreams.swapRefused(directUrl)) {
            directUrl = LocalServerYouTubeStreams.swapped(directUrl)
        } else {
            dropAfterDenial(directUrl, cacheKey, videoId)
            // Fresh retry URL, or same one if re-extraction found nothing better: forwarded response always live, unconsumed.
            directUrl = resolve(useCache = false) ?: directUrl
        }
        serverLog("Retrying stream from: $directUrl")
        response = fetchFromCdn(directUrl, cacheKey, rangeHeader)
    }

    response.use { r ->
        val code = r.code
        serverLog("Incoming Range = $rangeHeader CDN status=$code itag=$requestedItag")

        if (rangeHeader != null && code != 206) {
            serverLog("WARNING: Range requested ($rangeHeader) but CDN returned $code")
        }

        var type = r.header("Content-Type")
        if (type == null) {
            type = if (requestedItag == 140) "audio/mp4" else "video/mp4"
            if (requestedItag == -1) {
                // Itag-less services: manifest mtype decides.
                type =
                    when (mediaType) {
                        "audio" -> "audio/mp4"
                        "video" -> "video/mp4"
                        else -> "application/octet-stream"
                    }
            }
        }
        val headers =
            mapOf(
                "Content-Range" to r.header("Content-Range"),
                "Accept-Ranges" to (r.header("Accept-Ranges") ?: "bytes"),
                "Access-Control-Allow-Origin" to "*",
                "Access-Control-Allow-Headers" to "*",
                "Access-Control-Expose-Headers" to "*",
            ).filterValues { it != null }.mapValues { it.value!! }

        if (code == 206) serverLog("Successfully returning 206 Partial Content to client")

        reply.stream(code, type, r.header("Content-Length")?.toLongOrNull(), headers) { out ->
            try {
                r.body?.byteStream()?.copyTo(out, 65536)
            } catch (e: IOException) {
                // Client disconnected (pause/seek).
                serverLog("Stream proxy: Client connection closed.")
            }
        }
    }
}

internal fun fetchFromCdn(
    directUrl: String,
    cacheKey: String,
    rangeHeader: String?,
): okhttp3.Response {
    val reqBuilder =
        okhttp3.Request
            .Builder()
            .url(directUrl)
            .header("User-Agent", cdnUserAgent(directUrl, cacheKey))

    if (directUrl.contains("googlevideo.com")) {
        StreamDenialClassifier.clientOf(directUrl)?.let { client ->
            GoogleVideoRequestPolicy.headers(client).forEach { (name, value) -> reqBuilder.header(name, value) }
        }
    }

    if (BilibiliCdn.isCdnUrl(directUrl) || directUrl.contains("bilibili.com")) {
        BilibiliCdn.headers().forEach { (name, value) -> reqBuilder.header(name, value) }
    }

    if (rangeHeader != null) {
        reqBuilder.removeHeader("Range")
        reqBuilder.addHeader("Range", rangeHeader)
        serverLog("Forwarding Range to CDN: $rangeHeader")
    }

    return LocalServerCaches.httpClient.newCall(reqBuilder.build()).execute()
}

internal fun repCacheKey(
    serviceId: Int,
    mediaUrl: String,
    repId: String,
) = "${serviceId}_${mediaUrl}_rep_$repId"

internal fun RequestContext.handleSubtitlesProxy(
    reply: Reply,
    params: Query,
) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]
    val lang = params["lang"]
    val isAuto = "true" == params["auto"]

    val info = source.streamInfo(serviceId, mediaUrl ?: throw BadRequestException("Missing 'id' parameter"))

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
        LocalServerCaches.httpClient.newCall(req).execute().use { response ->
            val bodyBytes = (response.body?.string() ?: "").withoutCueSettings().toByteArray(Charsets.UTF_8)
            reply.sendBytes(200, bodyBytes, "text/vtt; charset=UTF-8", mapOf("Access-Control-Allow-Origin" to "*"))
        }
    } else {
        reply.send(404, "Subtitles not found", "text/plain; charset=UTF-8")
    }
}

/**
 * YouTube WebVTT: placement on every cue timing line ("align:start position:0%") pins captions left in the player. Dropped → centred.
 */
private fun String.withoutCueSettings(): String = replace(Regex("""^(\S+ --> \S+)[ \t]+\S.*$""", RegexOption.MULTILINE), "$1")
