package org.schabi.newpipe.localserver

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.localserver.LocalHttpServer.ClientHandler
import java.io.IOException
import java.io.OutputStream

// Stream/manifest/subtitle proxying and the static CSS/JS assets, split out of LocalHttpServer.kt
// as extension functions on ClientHandler purely to keep that file from growing without bound -
// no behavior changed by the move. getCachedExtractor/getResolutionHeight/
// audioTrackPriorityComparator/isOriginalAudioTrack/normalizeAudioCodec/log/streamUrlCache/
// httpClient are LocalHttpServer's companion object members, qualified here because they're
// outside that class's own lexical scope in this file, not because they're private - none of
// them are.

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
    // Must be part of the cache key: for itag-less services (Bilibili) the video and audio
    // requests share itag=-1 and trackId, so without mtype they'd collide and the second one
    // would be served the first one's URL.
    val mediaType = params["mtype"]
    val cacheKey = serviceId.toString() + "_" + mediaUrl + "_" + requestedItag +
            (if (requestedTrackId != null) "_$requestedTrackId" else "") +
            (if (mediaType != null) "_$mediaType" else "")
    var directUrl = LocalHttpServer.streamUrlCache.get(cacheKey)

    if (directUrl == null) {
        val service = NewPipe.getService(serviceId)
        val extractor = service.getStreamExtractor(mediaUrl)
        extractor.fetchPage()

        // Streams from services without itags (Bilibili reports -1 for everything) can't be
        // picked by itag, so the DASH manifest tags each Representation with mtype and we select
        // the best stream of that kind instead.
        if (requestedItag == -1 && mediaType != null) {
            if ("audio" == mediaType) {
                val audioOnly = extractor.audioStreams
                if (audioOnly != null && !audioOnly.isEmpty()) {
                    var best = audioOnly[0]
                    for (candidate in audioOnly) {
                        if (candidate.averageBitrate > best.averageBitrate) {
                            best = candidate
                        }
                    }
                    directUrl = best.content
                }
            } else if ("video" == mediaType) {
                var videoOnly = extractor.videoOnlyStreams
                if (videoOnly == null || videoOnly.isEmpty()) {
                    videoOnly = extractor.videoStreams
                }
                if (videoOnly != null && !videoOnly.isEmpty()) {
                    directUrl = videoOnly[0].content
                }
            }
        }

        if (directUrl == null && requestedItag != -1) {
            for (stream in extractor.videoStreams) {
                if (stream.itag == requestedItag) {
                    directUrl = stream.content
                    break
                }
            }
            if (directUrl == null) {
                for (stream in extractor.videoOnlyStreams) {
                    if (stream.itag == requestedItag) {
                        directUrl = stream.content
                        break
                    }
                }
            }
            if (directUrl == null) {
                for (stream in extractor.audioStreams) {
                    if (stream.itag == requestedItag) {
                        val streamTrackId = stream.audioTrackId ?: ""
                        val reqTrackId = requestedTrackId ?: ""
                        if (streamTrackId == reqTrackId) {
                            directUrl = stream.content
                            break
                        }
                    }
                }
            }
        }

        if (directUrl == null) {
            val qualityParam = params["quality"]
            val startTimeParam = params["start_time"]
            var startTime = 0.0
            if (startTimeParam != null) {
                try {
                    startTime = startTimeParam.toDouble()
                } catch (e: Exception) {
                }
            }

            val targetQuality = qualityParam ?: dbHelper.nativeVideoQuality()
            val targetHeight = LocalHttpServer.getResolutionHeight(targetQuality)

            // Fallback to progressive stream
            val progressiveStreams = extractor.videoStreams
            if (progressiveStreams != null && !progressiveStreams.isEmpty()) {
                var selectedStream: VideoStream? = null
                var bestHeight = -1
                for (stream in progressiveStreams) {
                    val height = LocalHttpServer.getResolutionHeight(stream.resolution)
                    if (height <= targetHeight) {
                        if (height > bestHeight) {
                            bestHeight = height
                            selectedStream = stream
                        }
                    }
                }
                if (selectedStream == null) {
                    // If no stream is <= targetHeight, pick the highest quality one available
                    for (stream in progressiveStreams) {
                        val height = LocalHttpServer.getResolutionHeight(stream.resolution)
                        if (height > bestHeight) {
                            bestHeight = height
                            selectedStream = stream
                        }
                    }
                }
                if (selectedStream == null) {
                    selectedStream = progressiveStreams[0]
                }
                directUrl = selectedStream.content
            } else {
                try {
                    val hlsUrl = extractor.hlsUrl
                    if (!hlsUrl.isNullOrEmpty()) {
                        directUrl = hlsUrl
                    }
                } catch (e: Exception) {
                    // ignore
                }
                if (directUrl == null) {
                    val rawAudioStreams = extractor.audioStreams
                    if (rawAudioStreams != null && !rawAudioStreams.isEmpty()) {
                        // 1. Sort using NewPipe-like ranking to find the best track at index 0.
                        // This fork replaced the AudioTrackType enum (ORIGINAL/DUBBED/SECONDARY/
                        // DESCRIPTIVE) with plain audioTrackName/audioLocale strings and no longer
                        // distinguishes DUBBED/SECONDARY/DESCRIPTIVE from each other; "original" is
                        // now signalled by the literal "(original)" suffix in audioTrackName (see
                        // YoutubeStreamExtractor's HLS master-manifest parsing).
                        var audioStreams: MutableList<AudioStream> = ArrayList(rawAudioStreams)
                        audioStreams.sortWith(LocalHttpServer.audioTrackPriorityComparator())
                        // 2. Keep only streams of the best track
                        val bestTrackId = audioStreams[0].audioTrackId
                        val filteredStreams = audioStreams.filter { it.audioTrackId == bestTrackId }
                        if (filteredStreams.isNotEmpty()) {
                            audioStreams = filteredStreams.toMutableList()
                        }
                        directUrl = audioStreams[0].content
                    }
                }
            }
        }

        if (directUrl != null) {
            LocalHttpServer.streamUrlCache.put(cacheKey, directUrl, 3600000)
        }
    }

    if (directUrl != null) {
        LocalHttpServer.log("Proxying stream from: $directUrl")

        // Use matching User-Agent for YouTube streams depending on the client (c) parameter to avoid 403 Forbidden
        var ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        if (directUrl.contains("googlevideo.com")) {
            try {
                ua = if (directUrl.contains("c=IOS") || directUrl.contains("c=ios")) {
                    org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getIosUserAgent(null)
                } else if (directUrl.contains("c=VISIONOS") || directUrl.contains("c=visionos")) {
                    org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getVisionOsUserAgent(null)
                } else {
                    org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getAndroidUserAgent(null)
                }
            } catch (e: Exception) {
            }
        }

        // Build remote request
        val reqBuilder = okhttp3.Request.Builder()
            .url(directUrl)
            .header("User-Agent", ua)

        // Bilibili's CDN enforces hotlink protection and returns 403 for any request without a
        // matching Referer. Unlike a browser, this proxy has to set it itself.
        if (directUrl.contains("bilivideo.com") || directUrl.contains("bilibili.com") ||
            directUrl.contains("akamaized.net")) {
            reqBuilder.header("Referer", "https://www.bilibili.com/")
            reqBuilder.header("Origin", "https://www.bilibili.com")
        }

        // Forward Range header if client sent it
        if (rangeHeader != null) {
            reqBuilder.removeHeader("Range")
            reqBuilder.addHeader("Range", rangeHeader)
            LocalHttpServer.log("Forwarding Range to CDN: $rangeHeader")
        }

        LocalHttpServer.httpClient.newCall(reqBuilder.build()).execute().use { response ->
            val code = response.code
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
                val v = response.header(h)
                if (v != null) {
                    headBuilder.append(h).append(": ").append(v).append("\r\n")
                }
            }

            // Ensure Content-Type is set if missing
            if (response.header("Content-Type") == null) {
                var defaultType = if (requestedItag == 140) "audio/mp4" else "video/mp4"
                if (requestedItag == -1) {
                    // For itag-less services, mtype from the manifest is authoritative; handing a
                    // DASH player application/octet-stream can break playback.
                    defaultType = if ("audio" == mediaType) "audio/mp4"
                    else if ("video" == mediaType) "video/mp4"
                    else "application/octet-stream"
                }
                headBuilder.append("Content-Type: ").append(defaultType).append("\r\n")
            }

            // Ensure Accept-Ranges is set for DASH
            if (response.header("Accept-Ranges") == null) {
                headBuilder.append("Accept-Ranges: bytes\r\n")
            }

            headBuilder.append("Access-Control-Allow-Origin: *\r\n")
            headBuilder.append("Access-Control-Allow-Headers: *\r\n")
            headBuilder.append("Access-Control-Expose-Headers: *\r\n")
            headBuilder.append("\r\n")

            if (code == 206) LocalHttpServer.log("Successfully returning 206 Partial Content to client")

            os.write(headBuilder.toString().toByteArray(Charsets.UTF_8))
            os.flush()

            // Pipe body bytes
            val responseBody = response.body
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
                    // Client disconnected (e.g. paused/sought)
                    LocalHttpServer.log("Stream proxy: Client connection closed.")
                }
            }
            os.flush()
        }
    } else {
        sendResponse(os, 404, "Stream URL not found.", "text/plain; charset=UTF-8")
    }
}

@Throws(Exception::class)
internal fun ClientHandler.handleManifestProxy(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]!!

    val service = NewPipe.getService(serviceId)
    // Shares one page extraction with handleWatchContent/handleAudioWatch for this same video,
    // instead of each doing its own independent fetchPage().
    val extractor = LocalHttpServer.getCachedExtractor(service, serviceId, mediaUrl)

    // Construct standard DASH manifest (MPD) locally using extracted stream lists. extractor may
    // be shared with a concurrent handleWatchContent/handleAudioWatch request for the same video
    // (getCachedExtractor above) - synchronized on it since the extractor library's getters
    // aren't guaranteed safe to call from two threads at once, unlike the plain-data
    // VideoStream/AudioStream objects they return.
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

    // Video AdaptationSet (Adaptive / Video-Only streams)
    val rawVideoStreams: List<VideoStream>?
    synchronized(extractor) {
        rawVideoStreams = extractor.videoOnlyStreams
    }
    val videoStreams = (rawVideoStreams ?: emptyList()).filter { vs -> vs.format == org.schabi.newpipe.extractor.MediaFormat.MPEG_4 }

    if (videoStreams.isNotEmpty()) {
        sb.append("    <AdaptationSet id=\"0\" mimeType=\"video/mp4\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">\n")
        val seenVideoItags = HashSet<Int>()
        for (vs in videoStreams) {
            val itag = vs.itag
            if (seenVideoItags.contains(itag)) {
                continue
            }
            seenVideoItags.add(itag)

            var bitrate = vs.bitrate.toLong()
            if (bitrate <= 0) {
                bitrate = 1000000L
            }
            // Extractors usually return bps. If it's suspiciously low, we might scale, but the
            // previous scaling (bitrate < 100000) was causing 360p (83kbps) to be scaled to
            // 83Mbps while 1080p (495kbps) was left at 0.5Mbps. Let's use a much lower threshold
            // or just trust the extractor.
            if (bitrate < 5000) {
                bitrate *= 1000
            }
            val codec = vs.codec
            val width = vs.width
            val height = vs.height
            val fps = vs.fps

            val initStart = vs.initStart
            val initEnd = vs.initEnd
            val indexStart = vs.indexStart
            val indexEnd = vs.indexEnd

            if (initStart < 0 || initEnd < 0 || indexStart < 0 || indexEnd < 0) {
                continue  // Skip streams without correct index range markers
            }

            // itag is a YouTube concept; Bilibili streams report -1, which would make the video
            // and audio Representations share an identical BaseURL. mtype gives the proxy a way
            // to tell them apart when itag can't.
            val proxyUrl = "/stream?serviceId=" + serviceId + "&amp;id=" + java.net.URLEncoder.encode(mediaUrl, "UTF-8") + "&amp;itag=" + itag + "&amp;mtype=video"
            // Pre-warm the cache handleStreamProxy checks first, with the exact key it will
            // compute for this Representation's proxy URL - the player requests this itag moments
            // after loading this manifest, and without this it would otherwise redo the full page
            // extraction that was just done to build this manifest.
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

    // Audio AdaptationSet
    val rawAudioStreams: List<AudioStream>?
    synchronized(extractor) {
        rawAudioStreams = extractor.audioStreams
    }
    var audioStreams = (rawAudioStreams ?: emptyList()).filter { it.format == org.schabi.newpipe.extractor.MediaFormat.M4A }
    if (audioStreams.isNotEmpty()) {
        val targetAudioTrack = params["audio_track"]
        var selectedTrackId: String

        // If a track is explicitly selected, find it
        if (targetAudioTrack != null) {
            selectedTrackId = targetAudioTrack
        } else {
            // Default to best track using NewPipe comparator
            val sorted = audioStreams.sortedWith(LocalHttpServer.audioTrackPriorityComparator())
            audioStreams = sorted

            val bestId = sorted[0].audioTrackId
            selectedTrackId = bestId ?: ""
        }

        // Filter to only keep streams matching the selectedTrackId
        val finalTrackId = selectedTrackId
        audioStreams = audioStreams.filter { (it.audioTrackId ?: "") == finalTrackId }

        if (audioStreams.isNotEmpty()) {
            // Sort by quality (bitrate descending)
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

            sb.append("    <AdaptationSet id=\"1\" mimeType=\"audio/mp4\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\"").append(langStr).append(labelStr).append(">\n")

            // Add Role element based on whether this is the original track. The fork no longer
            // classifies dub/description/secondary separately (see isOriginalAudioTrack), so any
            // non-original track is labelled "dub".
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

                var bitrate = asStream.averageBitrate.toLong()
                if (bitrate <= 0) {
                    bitrate = asStream.bitrate.toLong()
                }
                if (bitrate <= 0) {
                    bitrate = 128000L
                }
                if (bitrate < 1000) {
                    bitrate *= 1000
                }
                val codec = LocalHttpServer.normalizeAudioCodec(asStream.codec)

                val initStart = asStream.initStart
                val initEnd = asStream.initEnd
                val indexStart = asStream.indexStart
                val indexEnd = asStream.indexEnd

                if (initStart < 0 || initEnd < 0 || indexStart < 0 || indexEnd < 0) {
                    continue  // Skip streams without index range markers
                }

                val proxyUrl = "/stream?serviceId=" + serviceId + "&amp;id=" + java.net.URLEncoder.encode(mediaUrl, "UTF-8") + "&amp;itag=" + itag + "&amp;mtype=audio" + (if (finalTrackId.isNotEmpty()) "&amp;trackId=" + java.net.URLEncoder.encode(finalTrackId, "UTF-8") else "")
                // Same cache pre-warm as the video Representations above - key must match
                // handleStreamProxy's construction exactly (itag, then trackId if present).
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

    val service = NewPipe.getService(serviceId)
    val info = StreamInfo.getInfo(service, mediaUrl)

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
            val bodyBytes = response.body?.bytes() ?: ByteArray(0)
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

// The '?v=' cache-busting param on the request is intentionally ignored here - it only exists so
// the browser treats a content change as a different URL (see
// HtmlRendererCommon.STATIC_ASSET_VERSION); the response is the same file regardless of its
// value. max-age=31536000 (1 year) + immutable is safe specifically because of that: any real
// edit to HtmlStyles.CSS changes the version tag, which changes the URL, which bypasses this old
// cache entry entirely rather than serving stale content from it.
@Throws(Exception::class)
internal fun ClientHandler.handleStaticCss(os: OutputStream) {
    sendResponse(os, 200, HtmlStyles.CSS, "text/css; charset=UTF-8", "public, max-age=31536000, immutable")
}

@Throws(Exception::class)
internal fun ClientHandler.handleStaticJs(os: OutputStream) {
    sendResponse(os, 200, HtmlScripts.RAW_JS, "application/javascript; charset=UTF-8", "public, max-age=31536000, immutable")
}
