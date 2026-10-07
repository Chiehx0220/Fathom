package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliLink
import io.github.aedev.flow.data.recommendation.InteractionType
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

// Handler groups are extension functions in sibling files.
internal class ClientHandler(
    private val socket: Socket,
    internal val dbHelper: HistoryDbHelper,
    private val context: android.content.Context,
    private val executorService: ExecutorService,
) : Runnable {
    // Set once run() has read the request, so sendResponse() can check Accept-Encoding.
    private var requestHeaders: MutableMap<String, String>? = null

    internal val remoteAddress: String? get() = socket.inetAddress.hostAddress

    override fun run() {
        try {
            socket.tcpNoDelay = true
            // A browser opens spare connections it never sends on; without a limit each would hold a thread for good.
            socket.soTimeout = REQUEST_READ_TIMEOUT_MS
            val head = readRequestHead(socket.getInputStream()) ?: return
            socket.soTimeout = 0

            val requestLine = head[0].split(" ")
            if (requestLine.size < 2) return
            val method = requestLine[0]
            val rawUri = requestLine[1]
            val path = rawUri.substringBefore('?')
            val query = if (rawUri.contains('?')) rawUri.substringAfter('?') else null
            val params = parseQueryParams(query)
            serverLog("Request: $method $path" + (if (query != null) "?$query" else ""))

            val requestHeaders = parseRequestHeaders(head)
            this.requestHeaders = requestHeaders

            socket.getOutputStream().use { os ->
                if ("OPTIONS".equals(method, ignoreCase = true)) {
                    os.write(CORS_PREFLIGHT.toByteArray(Charsets.UTF_8))
                    os.flush()
                    return
                }
                try {
                    val route = ROUTES[path]
                    if (route != null) {
                        route(this, os, params, requestHeaders)
                    } else {
                        sendResponse(os, 404, "Page Not Found", "text/plain; charset=UTF-8")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    serverLog("Error during route handling: " + e.message)
                    sendResponse(os, 500, "Internal Server Error:\n" + e.toString(), "text/plain; charset=UTF-8")
                }
            }
        } catch (e: IOException) {
            // The client went away, or never sent a request.
        } catch (e: Exception) {
            serverLog("Request failed: $e")
        } finally {
            try {
                socket.close()
            } catch (ignored: IOException) {
            }
        }
    }

    // Merges subscribed channels' uploads into one newest-first feed (capped at 60).
    // Each channel is resolved by its own URL, since subscriptions mix services.
    internal fun fetchSubscriptionFeed(channels: List<InfoItem>?): List<InfoItem> {
        var feed = ArrayList<InfoItem>()
        if (channels == null || channels.isEmpty()) {
            return feed
        }
        val futures = ArrayList<Future<List<InfoItem>>>()
        for (channel in channels) {
            val url = channel.url
            // Backfill the avatar from the subscription; upload listings carry none.
            val subscribedAvatarUrl = channel.thumbnailUrl?.takeIf { it.isNotBlank() }
            futures.add(
                executorService.submit(
                    Callable {
                        val uploads =
                            if (BilibiliLink.isBilibili(url)) {
                                LocalServerBilibili.uploads(
                                    dbHelper.appContext,
                                    url,
                                    channel.name.orEmpty(),
                                    subscribedAvatarUrl.orEmpty(),
                                )
                            } else {
                                LocalServerMedia.fetchChannelUploads(NewPipe.getServiceByUrl(url), url)
                            }
                        if (!subscribedAvatarUrl.isNullOrEmpty()) {
                            for (upload in uploads) {
                                if (upload is StreamInfoItem && upload.uploaderAvatarUrl.isNullOrBlank()) {
                                    upload.uploaderAvatarUrl = subscribedAvatarUrl
                                }
                            }
                        }
                        uploads
                    },
                ),
            )
        }
        for ((index, future) in futures.withIndex()) {
            try {
                // Bilibili's first request of a session sets up cookies and a signing key, which takes longer.
                val waitSeconds = if (BilibiliLink.isBilibili(channels[index].url)) 20L else 5L
                val res = future.get(waitSeconds, TimeUnit.SECONDS)
                if (res != null) {
                    feed.addAll(res)
                }
            } catch (e: Exception) {
                serverLog("Future timeout/error fetching subscription feed: " + e.message)
            }
        }
        feed.sortWith(
            Comparator { a, b ->
                val dateA = uploadDateOf(a)
                val dateB = uploadDateOf(b)
                if (dateA == null && dateB == null) {
                    0
                } else if (dateA == null) {
                    1
                } else if (dateB == null) {
                    -1
                } else {
                    dateB.compareTo(dateA)
                }
            },
        )
        if (feed.size > 60) {
            feed = ArrayList(feed.subList(0, 60))
        }
        return feed
    }

    // getUploadDate() is null without a precise timestamp; items without one sort last.
    private fun uploadDateOf(item: InfoItem): java.time.OffsetDateTime? {
        if (item is StreamInfoItem) {
            val dw = item.uploadDate
            if (dw != null) {
                return dw.offsetDateTime()
            }
        }
        return null
    }

    internal fun getServiceId(params: Map<String, String>): Int {
        val raw = params["serviceId"]
        if (raw != null) {
            try {
                val id = raw.trim().toInt()
                if (LocalServerMedia.isSupportedService(id)) {
                    return id
                }
            } catch (ignored: NumberFormatException) {
            }
        }
        return LocalServerMedia.SERVICE_YOUTUBE
    }

    private fun parseQueryParams(query: String?): MutableMap<String, String> {
        val params = HashMap<String, String>()
        if (query.isNullOrEmpty()) return params
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            try {
                val key = URLDecoder.decode(if (idx > 0) pair.substring(0, idx) else pair, "UTF-8")
                val value = if (idx > 0 && pair.length > idx + 1) URLDecoder.decode(pair.substring(idx + 1), "UTF-8") else ""
                params[key] = value
            } catch (e: Exception) {
            }
        }
        return params
    }

    @Throws(IOException::class)
    internal fun sendResponse(
        os: OutputStream,
        code: Int,
        content: String,
        contentType: String,
    ) {
        sendResponse(os, code, content, contentType, null)
    }

    // cacheControl: header value, or null to omit; only the static handlers pass one.
    @Throws(IOException::class)
    internal fun sendResponse(
        os: OutputStream,
        code: Int,
        content: String,
        contentType: String,
        cacheControl: String?,
    ) {
        var bytes = content.toByteArray(Charsets.UTF_8)
        val status = if (code == 200) "OK" else (if (code == 404) "Not Found" else "Internal Server Error")

        // gzip for HTML/CSS/JS responses; not for media (already compressed) or bodies under 512 bytes.
        var contentEncodingHeader = ""
        val currentRequestHeaders = requestHeaders
        if (bytes.size > 512 && currentRequestHeaders != null) {
            val acceptEncoding = currentRequestHeaders["accept-encoding"]
            if (acceptEncoding != null && acceptEncoding.lowercase(Locale.US).contains("gzip")) {
                val gzBuffer = java.io.ByteArrayOutputStream()
                java.util.zip.GZIPOutputStream(gzBuffer).use { gzos ->
                    gzos.write(bytes)
                }
                bytes = gzBuffer.toByteArray()
                contentEncodingHeader = "Content-Encoding: gzip\r\n"
            }
        }

        val response =
            "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                contentEncodingHeader +
                (if (cacheControl != null) "Cache-Control: $cacheControl\r\n" else "") +
                "Content-Length: " + bytes.size + "\r\n" +
                "Connection: close\r\n\r\n"
        os.write(response.toByteArray(Charsets.UTF_8))
        os.write(bytes)
        os.flush()
    }

    @Throws(IOException::class)
    internal fun sendRedirect(
        os: OutputStream,
        url: String,
    ) {
        val response =
            "HTTP/1.1 302 Found\r\n" +
                "Location: $url\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"
        os.write(response.toByteArray(Charsets.UTF_8))
        os.flush()
    }

    @Throws(Exception::class)
    internal fun handleSearchHistory(
        os: OutputStream,
        params: Map<String, String>,
    ) {
        val deleteQuery = params["delete"]
        if (!deleteQuery.isNullOrEmpty()) {
            dbHelper.nativeDeleteSearchQuery(deleteQuery)
            sendResponse(os, 200, "{\"status\":\"success\"}", "application/json")
            return
        }
        val history = dbHelper.nativeSearchHistory()
        val json = StringBuilder()
        json.append("[")
        for (i in history.indices) {
            json.append("\"").append(history[i].replace("\"", "\\\"")).append("\"")
            if (i < history.size - 1) {
                json.append(",")
            }
        }
        json.append("]")
        sendResponse(os, 200, json.toString(), "application/json")
    }

    internal fun filterItems(items: List<InfoItem>): List<InfoItem> {
        val hideWatched = dbHelper.nativeHideWatched()
        val hideShorts = dbHelper.nativeHideShorts()
        val blockedChannelIds = dbHelper.nativeBlockedChannelIds()

        val filtered = ArrayList<InfoItem>()
        val watchedUrls: Set<String> = if (hideWatched) dbHelper.nativeWatchedUrls() else emptySet()

        for (item in items) {
            if (hideWatched && watchedUrls.contains(item.url)) {
                continue
            }

            if (hideShorts && item is StreamInfoItem) {
                if (item.duration > 0 && item.duration <= 120) {
                    continue
                }
            }

            if (item is StreamInfoItem && blockedChannelIds.isNotEmpty()) {
                val channelId = channelUrlToId(item.uploaderUrl)
                if (channelId != null && blockedChannelIds.contains(channelId)) continue
            }

            filtered.add(item)
        }
        return filtered
    }

    // FlowNeuro signals: reported from handleApiVideo and the progress endpoint.
    // Best-effort: failures never affect playback or the DB write.
    internal fun reportFlowNeuroClick(
        info: StreamInfo,
        serviceId: Int,
    ) {
        dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.CLICK)
    }

    // Ranking delegates to the native FlowNeuroEngine (rankWithFlowNeuro()); used by handleApiRecommendations(), not handleApiHome().
    internal fun applyFlowNeuroRanking(
        items: List<InfoItem>,
        serviceId: Int,
    ): List<InfoItem> = dbHelper.rankWithFlowNeuro(items, serviceId)
}
