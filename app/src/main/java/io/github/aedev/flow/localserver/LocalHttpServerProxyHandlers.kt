package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import io.github.aedev.flow.localserver.LocalHttpServer.ClientHandler
import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.player.error.StreamDenialKind
import io.github.aedev.flow.player.stream.ClientGateTracker
import io.github.aedev.flow.utils.videoIdFromUrl
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
            directUrl.contains("c=IOS") || directUrl.contains("c=ios") ->
                org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getIosUserAgent(null)
            directUrl.contains("c=VISIONOS") || directUrl.contains("c=visionos") ->
                org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getVisionOsUserAgent(null)
            else -> org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getAndroidUserAgent(null)
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
        StreamDenialKind.ATTESTATION_GATED -> ClientGateTracker.reportGated(client)
        StreamDenialKind.TOKEN_REJECTED -> ClientGateTracker.reportRefused(client)
        else -> {}
    }
    videoId?.let { LocalServerYouTubeStreams.invalidate(it) }
    LocalHttpServer.streamUrlCache.remove(cacheKey)
    LocalHttpServer.streamUaCache.remove(cacheKey)
}

@Throws(Exception::class)
internal fun ClientHandler.handleStreamProxy(os: OutputStream, params: Map<String, String>, requestHeaders: Map<String, String>) {
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
    // mtype is part of the cache key: itag-less services (Bilibili) share itag=-1 between video and audio.
    val mediaType = params["mtype"]
    val resolvedMediaUrl = requireNotNull(mediaUrl) { "Missing 'id' parameter" }
    val videoId = videoIdFromUrl(resolvedMediaUrl)
    val cacheKey = serviceId.toString() + "_" + mediaUrl + "_" + requestedItag +
            (if (requestedTrackId != null) "_$requestedTrackId" else "") +
            (if (mediaType != null) "_$mediaType" else "")

    // LocalServerSource.streams is always asked fresh here: a stream request wants its own extractor
    // fetch, not the one the manifest/watch page's cache is sharing.
    fun resolve(useCache: Boolean): String? {
        if (useCache) LocalHttpServer.streamUrlCache.get(cacheKey)?.let { return it }
        val extractor = LocalServerSource.streams(dbHelper.appContext, serviceId, resolvedMediaUrl, fresh = true)
        val url = resolveDirectUrl(extractor, requestedItag, requestedTrackId, mediaType, params) ?: return null
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
        headBuilder.append("HTTP/1.1 ").append(code).append(" ").append(statusText).append("\r\n")

        val headersToForward = arrayOf(
            "Content-Type",
            "Content-Length",
            "Content-Range",
            "Accept-Ranges"
        )

        for (h in headersToForward) {
            val v = r.header(h)
            if (v != null) {
                headBuilder.append(h).append(": ").append(v).append("\r\n")
            }
        }

        if (r.header("Content-Type") == null) {
            var defaultType = if (requestedItag == 140) "audio/mp4" else "video/mp4"
            if (requestedItag == -1) {
                // Itag-less services: the manifest's mtype decides.
                defaultType = if ("audio" == mediaType) "audio/mp4"
                else if ("video" == mediaType) "video/mp4"
                else "application/octet-stream"
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
    val reqBuilder = okhttp3.Request.Builder()
        .url(directUrl)
        .header("User-Agent", cdnUserAgent(directUrl, cacheKey))

    // Bilibili's CDN answers 403 without a matching Referer.
    if (directUrl.contains("bilivideo.com") || directUrl.contains("bilibili.com") ||
        directUrl.contains("akamaized.net")) {
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

/**
 * A Representation's `bandwidth`, in bps.
 *
 * NewPipe's static itag table - its fallback when a format carries no live bitrate - reports audio
 * bitrate in kbps, at most 256 (see its `ItagItem.ITAG_LIST`). A live bitrate, from InnerTube or from
 * NewPipe's own extraction, is never anywhere near that low: even the quietest 32kbps audio itag
 * reports as ~32000 bps. A raw value under the gap between those two ranges is the kbps leftover, not
 * a genuine bitrate, and gets rescaled; anything at or above it is already bps.
 */
private fun normalizedBandwidthBps(
    rawBitrate: Long,
    fallbackBps: Long,
): Long {
    var bitrate = rawBitrate
    if (bitrate <= 0) bitrate = fallbackBps
    if (bitrate < 5000) bitrate *= 1000
    return bitrate
}

@Throws(Exception::class)
internal fun ClientHandler.handleManifestProxy(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]!!

    // Cached: shares its extraction with the watch handlers.
    val extractor = LocalServerSource.streams(dbHelper.appContext, serviceId, mediaUrl, fresh = false)

    // Synchronized: extractor getters are not thread-safe.
    var durationSec: Double
    synchronized(extractor) {
        durationSec = extractor.length.toDouble()
    }
    if (durationSec <= 0) {
        durationSec = 1800.0 // fallback 30 mins if length not available
    }

    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
    sb.append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" type=\"static\" mediaPresentationDuration=\"PT").append(durationSec).append("S\" minBufferTime=\"PT1.5S\">\n")
    sb.append("  <Period duration=\"PT").append(durationSec).append("S\">\n")

    val rawVideoStreams: List<VideoStream>?
    synchronized(extractor) {
        rawVideoStreams = extractor.videoOnlyStreams
    }
    // Shared with the quality list via dashPlayableVideoOnlyStreams(): it must offer exactly what this manifest serves.
    val videoStreams = LocalHttpServer.dashPlayableVideoOnlyStreams(rawVideoStreams)

    if (videoStreams.isNotEmpty()) {
        sb.append("    <AdaptationSet id=\"0\" mimeType=\"video/mp4\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\n")
        for (vs in videoStreams) {
            val itag = vs.itag

            val initStart = vs.initStart
            val initEnd = vs.initEnd
            val indexStart = vs.indexStart
            val indexEnd = vs.indexEnd
            if (initStart < 0 || initEnd < 0 || indexStart < 0 || indexEnd < 0) {
                continue // Skip formats without index range markers - same guard the audio loop uses below.
            }

            val bitrate = normalizedBandwidthBps(vs.bitrate.toLong(), fallbackBps = 1000000L)
            val codec = vs.codec
            val width = vs.width
            val height = vs.height
            val fps = vs.fps

            // mtype keeps video and audio Representations apart when itag=-1 gives them the same BaseURL.
            val proxyUrl = "/stream?serviceId=" + serviceId + "&amp;id=" + java.net.URLEncoder.encode(mediaUrl, "UTF-8") + "&amp;itag=" + itag + "&amp;mtype=video"
            // Pre-warms handleStreamProxy's cache with this Representation's key.
            if (vs.content != null) {
                LocalHttpServer.streamUrlCache.put(serviceId.toString() + "_" + mediaUrl + "_" + itag + "_video", vs.content, 3600000)
            }
            sb.append("      <Representation id=\"").append(itag).append("\" bandwidth=\"").append(bitrate).append("\" codecs=\"").append(codec).append("\" width=\"").append(width).append("\" height=\"").append(height).append("\" frameRate=\"").append(fps).append("\" sar=\"1:1\">\n")
            sb.append("        <BaseURL>").append(proxyUrl).append("</BaseURL>\n")
            sb.append("        <SegmentBase indexRange=\"").append(indexStart).append("-").append(indexEnd).append("\" indexRangeExact=\"true\">\n")
            sb.append("          <Initialization range=\"").append(initStart).append("-").append(initEnd).append("\"/>\n")
            sb.append("        </SegmentBase>\n")
            sb.append("      </Representation>\n")
        }
        sb.append("    </AdaptationSet>\n")
    }

    val rawAudioStreams: List<AudioStream>?
    synchronized(extractor) {
        rawAudioStreams = extractor.audioStreams
    }
    val allM4aStreams = (rawAudioStreams ?: emptyList()).filter { it.format == org.schabi.newpipe.extractor.MediaFormat.M4A }
    if (allM4aStreams.isNotEmpty()) {
        // Each language/dub is its own audio AdaptationSet, so dash.js exposes them as player.audioTracks. The starting track goes first (dash.js picks the first): ?audio_track= if given, else NewPipe's best-track priority.
        val prioritySorted = allM4aStreams.sortedWith(LocalHttpServer.audioTrackPriorityComparator())
        val preferredTrackId = params["audio_track"] ?: (prioritySorted[0].audioTrackId ?: "")
        val orderedTrackIds = LinkedHashSet<String>()
        orderedTrackIds.add(preferredTrackId)
        for (s in prioritySorted) orderedTrackIds.add(s.audioTrackId ?: "")

        var audioAdaptationId = 1
        for (finalTrackId in orderedTrackIds) {
            var audioStreams = allM4aStreams.filter { (it.audioTrackId ?: "") == finalTrackId }

            if (audioStreams.isNotEmpty()) {
                audioStreams = audioStreams.sortedWith(Comparator { a, b ->
                    val brA = if (a.averageBitrate > 0) a.averageBitrate else a.bitrate
                    val brB = if (b.averageBitrate > 0) b.averageBitrate else b.bitrate
                    brB.toLong().compareTo(brA.toLong())
                })

                val firstStream = audioStreams[0]
                val locale = firstStream.audioLocale
                var langStr = ""
                if (locale != null) {
                    langStr = " lang=\"" + locale + "\""
                } else if (finalTrackId.isNotEmpty()) {
                    val dotIdx = finalTrackId.indexOf(".")
                    langStr = if (dotIdx != -1) {
                        " lang=\"" + finalTrackId.substring(0, dotIdx) + "\""
                    } else {
                        " lang=\"$finalTrackId\""
                    }
                }

                var labelStr = ""
                val trackName = firstStream.audioTrackName
                if (!trackName.isNullOrEmpty()) {
                    labelStr = " label=\"" + trackName.replace("\"", "&quot;") + "\""
                }

                sb.append("    <AdaptationSet id=\"").append(audioAdaptationId++).append("\" mimeType=\"audio/mp4\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\"").append(langStr).append(labelStr).append(">\n")

                // Any non-original track is "dub".
                if (firstStream.audioTrackId != null) {
                    val roleVal = if (LocalHttpServer.isOriginalAudioTrack(firstStream)) "main" else "dub"
                    sb.append("      <Role schemeIdUri=\"urn:mpeg:dash:role:2011\" value=\"").append(roleVal).append("\"/>\n")
                }

                val seenAudioItags = HashSet<Int>()
                for (asStream in audioStreams) {
                    val itag = asStream.itag
                    if (seenAudioItags.contains(itag)) {
                        continue
                    }
                    seenAudioItags.add(itag)

                    var rawBitrate = asStream.averageBitrate.toLong()
                    if (rawBitrate <= 0) rawBitrate = asStream.bitrate.toLong()
                    val bitrate = normalizedBandwidthBps(rawBitrate, fallbackBps = 128000L)
                    val codec = LocalHttpServer.normalizeAudioCodec(asStream.codec)

                    val initStart = asStream.initStart
                    val initEnd = asStream.initEnd
                    val indexStart = asStream.indexStart
                    val indexEnd = asStream.indexEnd

                    if (initStart < 0 || initEnd < 0 || indexStart < 0 || indexEnd < 0) {
                        continue  // Skip streams without index range markers
                    }

                    val proxyUrl = "/stream?serviceId=" + serviceId + "&amp;id=" + java.net.URLEncoder.encode(mediaUrl, "UTF-8") + "&amp;itag=" + itag + "&amp;mtype=audio" + (if (finalTrackId.isNotEmpty()) "&amp;trackId=" + java.net.URLEncoder.encode(finalTrackId, "UTF-8") else "")
                    // Cache key must match handleStreamProxy's (itag, then trackId).
                    if (asStream.content != null) {
                        val audioCacheKey = serviceId.toString() + "_" + mediaUrl + "_" + itag +
                                (if (finalTrackId.isNotEmpty()) "_$finalTrackId" else "") + "_audio"
                        LocalHttpServer.streamUrlCache.put(audioCacheKey, asStream.content, 3600000)
                    }
                    sb.append("      <Representation id=\"").append(itag).append("\" bandwidth=\"").append(bitrate).append("\" codecs=\"").append(codec).append("\" audioSamplingRate=\"44100\">\n")
                    sb.append("        <AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"2\"/>\n")
                    sb.append("        <BaseURL>").append(proxyUrl).append("</BaseURL>\n")
                    sb.append("        <SegmentBase indexRange=\"").append(indexStart).append("-").append(indexEnd).append("\" indexRangeExact=\"true\">\n")
                    sb.append("          <Initialization range=\"").append(initStart).append("-").append(initEnd).append("\"/>\n")
                    sb.append("        </SegmentBase>\n")
                    sb.append("      </Representation>\n")
                }
                sb.append("    </AdaptationSet>\n")
            }
        }
    }

    sb.append("  </Period>\n")
    sb.append("</MPD>\n")

    val manifestXml = sb.toString()
    LocalHttpServer.log("Generated local DASH manifest:\n$manifestXml")

    val bodyBytes = manifestXml.toByteArray(Charsets.UTF_8)

    val responseHeaders = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: application/dash+xml; charset=UTF-8\r\n" +
            "Content-Length: " + bodyBytes.size + "\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: close\r\n\r\n"
    os.write(responseHeaders.toByteArray(Charsets.UTF_8))
    os.write(bodyBytes)
    os.flush()
}

@Throws(Exception::class)
internal fun ClientHandler.handleSubtitlesProxy(os: OutputStream, params: Map<String, String>) {
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
        val req = okhttp3.Request.Builder()
            .url(subUrl!!)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        LocalHttpServer.httpClient.newCall(req).execute().use { response ->
            val bodyBytes = (response.body?.string() ?: "").withoutCueSettings().toByteArray(Charsets.UTF_8)
            val contentType = "text/vtt"
            val headers = "HTTP/1.1 200 OK\r\n" +
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
private fun String.withoutCueSettings(): String =
    replace(Regex("""^(\S+ --> \S+)[ \t]+\S.*$""", RegexOption.MULTILINE), "$1")

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
