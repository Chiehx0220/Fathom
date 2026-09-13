package org.schabi.newpipe.localserver

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.search.filter.Filter
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.StreamType

import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.player.stream.isOriginalAudioTrack
import io.github.aedev.flow.utils.SearchFilterResolver

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class LocalHttpServer(private val context: android.content.Context, private val port: Int) {

    interface LogListener {
        fun onLog(message: String)
    }

    fun interface LockStatusListener {
        fun onLockStatusChanged()
    }

    class ClientInfo(val name: String, val connection: org.java_websocket.WebSocket)

    companion object {
        @Volatile
        private var activeLockCode: String? = null
        @Volatile
        private var activeClientIp: String? = null
        @Volatile
        private var activeVideoTitle: String? = null
        private var lockStatusListener: LockStatusListener? = null
        @Volatile
        private var wsServer: RemoteWebSocketServer? = null
        private val pendingCommands = java.util.concurrent.LinkedBlockingQueue<String>()

        @JvmStatic
        fun getConnectedClients(): List<ClientInfo> {
            val list = ArrayList<ClientInfo>()
            val server = wsServer
            if (server != null) {
                for (conn in server.getConnections()) {
                    if (conn.isOpen) {
                        var name = conn.getAttachment<String>()
                        if (name == null || name.isEmpty()) {
                            name = try {
                                "Client (" + conn.remoteSocketAddress.address.hostAddress + ")"
                            } catch (e: Exception) {
                                "Client (Unknown)"
                            }
                        }
                        list.add(ClientInfo(name, conn))
                    }
                }
            }
            return list
        }

        @JvmStatic
        fun castToClient(conn: org.java_websocket.WebSocket?, videoUrl: String) {
            if (conn != null && conn.isOpen) {
                try {
                    conn.send("play_video:$videoUrl")
                    log("Casted play_video command to client connection.")
                } catch (e: Exception) {
                    log("Failed to send command to specific client: " + e.message)
                }
            }
        }

        @JvmStatic
        fun getAndClearPendingCommands(): List<String> {
            val copy = ArrayList<String>()
            var cmd: String?
            while (pendingCommands.poll().also { cmd = it } != null) {
                copy.add(cmd!!)
            }
            return copy
        }

        @JvmStatic
        fun addPendingCommand(cmd: String?) {
            if (pendingCommands.size > 500) {
                pendingCommands.poll() // Prevent infinite growth if client disconnects
            }
            pendingCommands.offer(cmd!!)

            wsServer?.broadcastCommand(cmd)
        }

        @JvmStatic
        fun setLockStatusListener(listener: LockStatusListener?) {
            lockStatusListener = listener
        }

        @JvmStatic
        fun isLocked(): Boolean {
            return activeLockCode != null
        }

        @JvmStatic
        fun getActiveClientIp(): String? {
            return activeClientIp
        }

        @JvmStatic
        fun getActiveVideoTitle(): String? {
            return activeVideoTitle
        }

        @JvmStatic
        fun getActiveLockCode(): String? {
            return activeLockCode
        }

        @JvmStatic
        fun releaseLock() {
            activeLockCode = null
            activeClientIp = null
            activeVideoTitle = null
            lockStatusListener?.onLockStatusChanged()
        }

        @JvmStatic
        fun tryLock(code: String, clientIp: String, title: String?): Boolean {
            val currentLockCode = activeLockCode
            if (currentLockCode == null) {
                activeLockCode = code
                activeClientIp = clientIp
                activeVideoTitle = title
                lockStatusListener?.onLockStatusChanged()
                return true
            } else if (currentLockCode == code) {
                activeVideoTitle = title
                lockStatusListener?.onLockStatusChanged()
                return true
            }
            return false
        }

        private var logListener: LogListener? = null
        private val streamUrlCache = StreamUrlCache()

        // Bridges handleWatchContent/handleAudioWatch (need a full StreamInfo for the page) and
        // handleManifestProxy (needs the raw stream lists for the DASH manifest) so loading one video
        // triggers a single page extraction instead of two - whichever of those handlers runs first
        // for a given video populates this for the other. Smaller than streamUrlCache since a
        // StreamExtractor holds parsed page data, not just a URL string.
        private val extractorCache = ExtractorCache()
        private val httpClient: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        @JvmStatic
        fun setLogListener(listener: LogListener?) {
            logListener = listener
        }

        @JvmStatic
        fun log(message: String) {
            logListener?.onLog(message)
        }

        @JvmStatic
        fun getVideoId(url: String?): String {
            if (url == null) return ""
            if (url.contains("v=")) {
                val start = url.indexOf("v=") + 2
                val end = url.indexOf("&", start)
                return if (end == -1) url.substring(start) else url.substring(start, end)
            }
            if (url.contains("/shorts/")) {
                val start = url.indexOf("/shorts/") + 8
                val end = url.indexOf("?", start)
                return if (end == -1) url.substring(start) else url.substring(start, end)
            }
            if (url.contains("youtu.be/")) {
                val start = url.indexOf("youtu.be/") + 9
                val end = url.indexOf("?", start)
                return if (end == -1) url.substring(start) else url.substring(start, end)
            }
            return url
        }

        private fun fetchChannelUploads(service: StreamingService, channelUrl: String): List<InfoItem> {
            try {
                val channelExtractor = service.getChannelExtractor(channelUrl)
                channelExtractor.fetchPage()
                val tabExtractor = resolveChannelTabExtractor(service, channelExtractor, "videos")
                tabExtractor.fetchPage()
                val pageItems: List<*>? = if (tabExtractor.initialPage != null) tabExtractor.initialPage.items else null
                val list = ArrayList<InfoItem>()
                if (pageItems != null) {
                    for (item in pageItems) {
                        if (item is InfoItem) {
                            list.add(item)
                        }
                    }
                }
                backfillUploaderUrl(list, channelUrl)
                return list
            } catch (e: Exception) {
                log("Failed to fetch uploads for channel $channelUrl: " + e.message)
                return ArrayList()
            }
        }

        // BilibiliChannelInfoItemWebAPIExtractor/ClientAPIExtractor (used for a channel's own upload
        // list in this pinned extractor fork) never override getUploaderUrl(), so it falls through to
        // StreamInfoItemExtractor's default implementation, which returns "" rather than null. Any
        // "click the uploader" link built from that empty string hits the channel route with an empty
        // id and fails with "URL not accepted: ". Since every item here is already known to belong to
        // channelUrl (that's what was just fetched), backfill it directly instead of leaving it blank.
        private fun backfillUploaderUrl(items: List<InfoItem>, channelUrl: String) {
            for (item in items) {
                if (item is StreamInfoItem) {
                    val currentUploaderUrl = item.uploaderUrl
                    if (currentUploaderUrl == null || currentUploaderUrl.trim().isEmpty()) {
                        item.setUploaderUrl(channelUrl)
                    }
                }
            }
        }

        // A service that doesn't support the generic tab-query mechanism (getChannelTabLHFactory()
        // returns null - Bilibili is the current example) instead exposes its tabs directly off the
        // channel extractor itself, each already carrying a FilterItem named after the tab
        // (ChannelTabs.VIDEOS/PLAYLISTS/...). getChannelTabExtractorFromId() calls
        // getChannelTabLHFactory().fromQuery(...) unconditionally, so it NPEs for such a service -
        // this picks the matching tab from getTabs() instead when there's no query factory to ask.
        private fun resolveChannelTabExtractor(
            service: StreamingService,
            channelExtractor: ChannelExtractor,
            tab: String,
        ): ChannelTabExtractor {
            if (service.channelTabLHFactory != null) {
                return service.getChannelTabExtractorFromId(channelExtractor.id, tab, channelExtractor.baseUrl)
            }
            val handler = channelExtractor.tabs.firstOrNull { handler -> handler.contentFilters.any { it.name == tab } }
                ?: channelExtractor.tabs.firstOrNull()
                ?: throw ExtractionException("Channel exposes no tabs: ${channelExtractor.url}")
            return service.getChannelTabExtractor(handler)
        }

        // Every paginated list extractor (search, a channel tab, a playlist) exposes the exact
        // same "resume from an already-serialized nextPage token, otherwise fetch the first page"
        // shape. PPE's own ListExtractorCompat is the shared implementation now (this file used to
        // carry a private copy, independently duplicated at 6 call sites before that).
        private fun <R : InfoItem> fetchInitialOrPage(extractor: ListExtractor<R>, nextPage: Page?): InfoItemsPage<R> =
            org.schabi.newpipe.extractor.compat.ListExtractorCompat.fetchInitialOrPage(extractor, nextPage)

        // KioskList.getDefaultKioskExtractor() returns a raw (unparameterized) KioskExtractor, so
        // this can't use the generic fetchInitialOrPage() above - it mirrors the same shape by hand
        // instead. Shared by handleHome/handleApiHome/handleApiRecommendations's identical
        // non-YouTube branch: every service other than YouTube has no personalized feed, so it
        // always falls back to its own native kiosk (trending) list regardless of the
        // (YouTube-only) homeFeedMode preference.
        private fun fetchKioskPage(service: StreamingService, nextPage: Page?): InfoItemsPage<*> {
            val kioskExtractor = service.kioskList.defaultKioskExtractor
            kioskExtractor.fetchPage()
            return if (nextPage != null) kioskExtractor.getPage(nextPage) else kioskExtractor.initialPage
        }

        // For a Bilibili bangumi (paid series) url, the comments link handler needs this episode's
        // bvid out of the same WatchDataCache the stream extractor populates - going through PPE's
        // BilibiliCommentsCompat instead of calling getCommentsExtractor() directly means a comments
        // request that beats /watch-content there doesn't come back with a bare NullPointerException.
        // Every other url/service is unaffected - the compat helper only ever primes anything for
        // bangumi/play/ urls.
        private fun commentsExtractorFor(service: StreamingService, videoUrl: String) =
            if (service is org.schabi.newpipe.extractor.services.bilibili.BilibiliService) {
                org.schabi.newpipe.extractor.services.bilibili.compat.BilibiliCommentsCompat
                    .getCommentsExtractor(service, videoUrl)
            } else {
                service.getCommentsExtractor(videoUrl)
            }

        // BulletCommentsInfoItem.getLastingTime() always returns -1 regardless of what's set (a
        // bug in the extractor library itself, not this app) - the on-screen duration a danmaku
        // comment should play for is hardcoded client-side instead, so it's not serialized here.
        @JvmStatic
        fun bulletCommentJson(item: BulletCommentsInfoItem): org.json.JSONObject {
            val json = org.json.JSONObject()
            json.put("text", item.commentText ?: "")
            json.put("time", (item.duration?.toMillis() ?: 0L) / 1000.0)
            json.put("color", String.format("#%06X", item.argbColor and 0xFFFFFF))
            json.put(
                "position",
                when (item.position) {
                    BulletCommentsInfoItem.Position.TOP -> "top"
                    BulletCommentsInfoItem.Position.BOTTOM -> "bottom"
                    else -> "scroll"
                },
            )
            json.put("size", item.relativeFontSize)
            return json
        }

        const val SERVICE_YOUTUBE = 0
        val SUPPORTED_SERVICE_IDS = intArrayOf(SERVICE_YOUTUBE, ServiceList.BiliBili.serviceId)

        // Sentinel "nextPage" value for the home feed's "Load More" - not a real Page (trending
        // kiosk has no pagination), see continueDiscoveryFeed().
        const val HOME_DISCOVERY_LOAD_MORE_TOKEN = "discovery-more"

        @JvmStatic
        fun isSupportedService(serviceId: Int): Boolean {
            for (id in SUPPORTED_SERVICE_IDS) {
                if (id == serviceId) {
                    return true
                }
            }
            return false
        }

        /**
         * Builds a search extractor with the default "all" content filter. An empty filter list is
         * NOT a safe substitute here: YoutubeFilters.evaluateSelectedFilters() has no graceful
         * default for it and throws ("we have a problem here") - the filter must be resolved by
         * name and passed explicitly.
         */
        @JvmStatic
        @Throws(ExtractionException::class)
        fun getDefaultSearchExtractor(service: StreamingService, query: String): SearchExtractor {
            // resolveContentFilters() looks "all" up by name, which doesn't exist for Bilibili
            // (BilibiliFilters has no "all" content filter - see its own doc comment) and used to
            // silently resolve to an empty list here, falling through to the no-filter
            // getSearchExtractor(query) overload. Bilibili's search then omits the required
            // search_type= query param entirely, which hits a differently-shaped API response
            // BilibiliSearchExtractor can't parse - the actual cause of Bilibili search failing.
            // resolveSearchContentFilters() is the fallback-aware version built for exactly this:
            // it lands on Bilibili's own "videos" default instead of silently going filterless.
            val defaultFilter =
                SearchFilterResolver.resolveSearchContentFilters(
                    service,
                    listOf(SearchFilterResolver.DEFAULT_CONTENT_FILTER_NAME),
                )
            return service.getSearchExtractor(query, defaultFilter, emptyList())
        }

        /**
         * Normalizes an audio codec string for a DASH manifest.
         *
         * Bilibili reports a bare `"mp4a"`, which is not a valid RFC 6381 codec string:
         * `MediaSource.isTypeSupported("audio/mp4;codecs=\"mp4a\"")` returns false, so the player
         * discards the only audio Representation and reports "No available working or supported
         * playlists". AAC-LC (`mp4a.40.2`) is what Bilibili actually serves. YouTube already
         * supplies fully-qualified strings, so those pass through untouched.
         */
        @JvmStatic
        fun normalizeAudioCodec(codec: String?): String {
            if (codec == null || codec.trim().isEmpty()) {
                return "mp4a.40.2"
            }
            val trimmed = codec.trim()
            if (trimmed == "mp4a") {
                return "mp4a.40.2"
            }
            return trimmed
        }

        // Shared by both the stream-proxy track selection and the DASH manifest generator.
        @JvmStatic
        fun isOriginalAudioTrack(stream: AudioStream): Boolean {
            return stream.isOriginalAudioTrack()
        }

        // Priority order for picking the "best" audio track when nothing more specific was requested:
        // original track first, then a locale match for the device's language, then an English
        // fallback, then highest bitrate. Was independently copy-pasted at multiple call sites (this
        // class's stream proxy and DASH manifest generator, and HtmlRenderer's default-track picker
        // for the UI dropdown) - HtmlRenderer's copy had silently drifted to a truncated version
        // missing the English-fallback and bitrate tiebreak, so the track the UI showed as "default"
        // could disagree with the one actually served. One shared comparator keeps them in sync.
        @JvmStatic
        fun audioTrackPriorityComparator(): Comparator<AudioStream> {
            val langCode = Locale.getDefault().language
            return Comparator { a, b ->
                val isOrigA = isOriginalAudioTrack(a)
                val isOrigB = isOriginalAudioTrack(b)
                if (isOrigA != isOrigB) {
                    return@Comparator if (isOrigA) -1 else 1
                }
                val localeA = a.audioLocale?.let { Locale.forLanguageTag(it.replace('_', '-')).language }
                val localeB = b.audioLocale?.let { Locale.forLanguageTag(it.replace('_', '-')).language }
                val langMatchA = (localeA != null && localeA.equals(langCode, ignoreCase = true))
                val langMatchB = (localeB != null && localeB.equals(langCode, ignoreCase = true))
                if (langMatchA != langMatchB) {
                    return@Comparator if (langMatchA) -1 else 1
                }
                val engMatchA = (localeA != null && localeA.equals("en", ignoreCase = true))
                val engMatchB = (localeB != null && localeB.equals("en", ignoreCase = true))
                if (engMatchA != engMatchB) {
                    return@Comparator if (engMatchA) -1 else 1
                }
                val brA = if (a.averageBitrate > 0) a.averageBitrate else a.bitrate
                val brB = if (b.averageBitrate > 0) b.averageBitrate else b.bitrate
                brB.toLong().compareTo(brA.toLong())
            }
        }

        // Used by this class's stream proxy for quality selection.
        @JvmStatic
        fun getResolutionHeight(resolution: String?): Int {
            if (resolution == null || resolution.isEmpty()) return 0
            try {
                // Split by 'p' (e.g. "720p60" -> "720") to ignore frame rate
                val parts = resolution.split(Regex("(?i)p"))
                if (parts.isNotEmpty()) {
                    val numeric = parts[0].replace(Regex("[^0-9]"), "")
                    return if (numeric.isEmpty()) 0 else numeric.toInt()
                }
            } catch (e: Exception) {
                // fallback
            }
            return 0
        }

        // Shared by handleWatchContent/handleAudioWatch/handleManifestProxy so that loading a single
        // video only ever runs the actual page extraction (service.getStreamExtractor + fetchPage())
        // once, no matter which of those three handlers gets there first for a given video.
        @JvmStatic
        @Throws(Exception::class)
        fun getCachedExtractor(service: StreamingService, serviceId: Int, mediaUrl: String): StreamExtractor {
            val key = serviceId.toString() + "_" + mediaUrl
            val cached = extractorCache.get(key)
            if (cached != null) {
                return cached
            }
            val extractor = service.getStreamExtractor(mediaUrl)
            extractor.fetchPage()
            extractorCache.put(key, extractor, 3600000)
            return extractor
        }

    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val threadPool: ExecutorService = Executors.newCachedThreadPool()
    private val dbHelper: HistoryDbHelper = HistoryDbHelper.getInstance(context)

    @Throws(IOException::class)
    fun startServer() {
        val serverSocket = ServerSocket(port)
        this.serverSocket = serverSocket
        isRunning = true
        log("Local server started on port $port")

        // Start WebSocket Server on port 8081
        if (wsServer == null) {
            val server = RemoteWebSocketServer(8081)
            wsServer = server
            server.start()
        }

        threadPool.execute {
            while (isRunning) {
                try {
                    val socket = serverSocket.accept()
                    threadPool.execute(ClientHandler(socket, dbHelper, context, threadPool))
                } catch (e: IOException) {
                    if (!isRunning) break
                    log("Socket accept error: " + e.message)
                }
            }
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            // ignore
        }
        val server = wsServer
        if (server != null) {
            try {
                server.stop()
            } catch (e: InterruptedException) {
                // ignore
            }
            wsServer = null
        }
        threadPool.shutdownNow()
        log("Local server stopped.")
    }

    private class ClientHandler(
        private val socket: Socket,
        private val dbHelper: HistoryDbHelper,
        private val context: android.content.Context,
        private val executorService: ExecutorService
    ) : Runnable {

        // Set once near the top of run() before any handler method runs, so sendResponse() can
        // check Accept-Encoding without every handler needing to thread the header map through.
        private var requestHeaders: MutableMap<String, String>? = null

        override fun run() {
            try {
                BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8")).use { reader ->
                    socket.getOutputStream().use { os ->
                        val requestLine = reader.readLine() ?: return

                        val parts = requestLine.split(" ")
                        if (parts.size < 2) return

                        val method = parts[0]
                        val rawUri = parts[1]

                        // Parse path and query parameters
                        var path = rawUri
                        var query: String? = null
                        val qIdx = rawUri.indexOf("?")
                        if (qIdx >= 0) {
                            path = rawUri.substring(0, qIdx)
                            query = rawUri.substring(qIdx + 1)
                        }

                        val params = parseQueryParams(query)
                        log("Request: $method $path" + (if (query != null) "?$query" else ""))

                        // Parse headers
                        val requestHeaders = HashMap<String, String>()
                        this.requestHeaders = requestHeaders
                        var headerLine: String?
                        while (reader.readLine().also { headerLine = it } != null && headerLine!!.isNotEmpty()) {
                            val line = headerLine!!
                            val colonIdx = line.indexOf(":")
                            if (colonIdx > 0) {
                                val name = line.substring(0, colonIdx).trim().lowercase(Locale.US)
                                val value = line.substring(colonIdx + 1).trim()
                                requestHeaders[name] = value
                            }
                        }

                        if ("OPTIONS".equals(method, ignoreCase = true)) {
                            val sb = "HTTP/1.1 204 No Content\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                                    "Access-Control-Allow-Headers: *\r\n" +
                                    "Access-Control-Expose-Headers: *\r\n" +
                                    "Access-Control-Max-Age: 86400\r\n" +
                                    "\r\n"
                            os.write(sb.toByteArray(Charsets.UTF_8))
                            os.flush()
                            return
                        }

                        // Read POST body if Content-Length is present
                        var postBody = ""
                        if ("POST".equals(method, ignoreCase = true)) {
                            val contentLengthHeader = requestHeaders["content-length"]
                            if (contentLengthHeader != null) {
                                try {
                                    val contentLength = contentLengthHeader.toInt()
                                    val buffer = CharArray(contentLength)
                                    var totalRead = 0
                                    while (totalRead < contentLength) {
                                        val read = reader.read(buffer, totalRead, contentLength - totalRead)
                                        if (read == -1) break
                                        totalRead += read
                                    }
                                    postBody = String(buffer, 0, totalRead)
                                } catch (e: Exception) {
                                    log("Error reading POST body: " + e.message)
                                }
                            }
                        }

                        // Detect device class (TV vs Phone)
                        val ua = requestHeaders["user-agent"]
                        var isTv = false
                        if (ua != null) {
                            val uaLower = ua.lowercase(Locale.US)
                            isTv = uaLower.contains("tv") || uaLower.contains("googletv") || uaLower.contains("androidtv") || uaLower.contains("smarttv") || uaLower.contains("appletv") || uaLower.contains("roku") || uaLower.contains("aftb") || uaLower.contains("aftt") || uaLower.contains("firetv")
                        }

                        try {
                            if (path == "/") {
                                handleHome(os, params, isTv)
                            } else if (path == "/search") {
                                handleSearch(os, params, isTv)
                            } else if (path == "/watch") {
                                handleWatch(os, params, isTv)
                            } else if (path == "/audio") {
                                handleAudioWatch(os, params, isTv)
                            } else if (path == "/watch-content") {
                                handleWatchContent(os, params, isTv)
                            } else if (path == "/comments") {
                                handleComments(os, params, isTv)
                            } else if (path == "/danmaku") {
                                handleDanmaku(os, params)
                            } else if (path == "/send-link" || path == "/play") {
                                handleSendLink(os, params, socket.inetAddress.hostAddress)
                            } else if (path == "/send-command") {
                                handleSendCommand(os, params)
                            } else if (path == "/poll-commands") {
                                handlePollCommands(os)
                            } else if (path == "/release-lock") {
                                handleReleaseLock(os, params)
                            } else if (path == "/history") {
                                handleHistory(os, params, isTv)
                            } else if (path == "/history_action") {
                                handleHistoryAction(os, params)
                            } else if (path == "/channel") {
                                handleChannel(os, params, isTv)
                            } else if (path == "/playlist") {
                                handlePlaylist(os, params, isTv)
                            } else if (path == "/stream") {
                                handleStreamProxy(os, params, requestHeaders)
                            } else if (path == "/manifest") {
                                handleManifestProxy(os, params)
                            } else if (path == "/subtitles") {
                                handleSubtitlesProxy(os, params)
                            } else if (path == "/log_client_capabilities") {
                                val supported = params["supported"]
                                val error = params["error"]
                                val playingQuality = params["playing_quality"]
                                val userAgent = requestHeaders["user-agent"]
                                if (error != null) {
                                    log("Client player error: $error | User-Agent: $userAgent")
                                } else if (playingQuality != null) {
                                    log("Client is playing quality: $playingQuality | User-Agent: $userAgent")
                                } else {
                                    log("Client connection capability check: DASH supported = $supported | User-Agent: $userAgent")
                                }
                                sendResponse(os, 200, "OK", "text/plain; charset=UTF-8")
                            } else if (path == "/api/player/play") {
                                handleApiPlayerPlay(os, params)
                            } else if (path == "/api/player/pause") {
                                handleApiPlayerPause(os)
                            } else if (path == "/api/player/resume") {
                                handleApiPlayerResume(os)
                            } else if (path == "/api/player/stop") {
                                handleApiPlayerStop(os)
                            } else if (path == "/search-history") {
                                handleSearchHistory(os, params)
                            } else if (path == "/subscriptions") {
                                handleSubscriptions(os, params, isTv)
                            } else if (path == "/subscribe") {
                                handleSubscribeAction(os, params)
                            } else if (path == "/block_channel") {
                                handleBlockChannelAction(os, params)
                            } else if (path == "/bookmark_playlist") {
                                handlePlaylistBookmarkAction(os, params)
                            } else if (path == "/static/style.css") {
                                handleStaticCss(os)
                            } else if (path == "/static/script.js") {
                                handleStaticJs(os)
                            } else if (path == "/settings") {
                                handleSettings(os, params, isTv)
                            } else if (path == "/watch-later") {
                                handleWatchLater(os, params, isTv)
                            } else if (path == "/watch_later_action") {
                                handleWatchLaterAction(os, params)
                            } else if (path == "/rate_video") {
                                handleRateVideoAction(os, params)
                            } else if (path == "/api/v1/search") {
                                handleApiSearch(os, params)
                            } else if (path == "/api/v1/home") {
                                handleApiHome(os, params)
                            } else if (path == "/api/v1/channel") {
                                handleApiChannel(os, params)
                            } else if (path == "/api/v1/video") {
                                handleApiVideo(os, params)
                            } else if (path == "/api/v1/comments") {
                                handleApiComments(os, params)
                            } else if (path == "/api/v1/watch_progress") {
                                handleApiWatchProgress(os, params)
                            } else if (path == "/api/v1/recommendations") {
                                handleApiRecommendations(os, params)
                            } else if (path == "/api/v1/ping") {
                                handleApiPing(os)
                            } else {
                                sendResponse(os, 404, "Page Not Found", "text/plain; charset=UTF-8")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            log("Error during route handling: " + e.message)
                            sendResponse(os, 500, "Internal Server Error:\n" + e.toString(), "text/plain; charset=UTF-8")
                        }
                    }
                }
            } catch (e: Exception) {
                // Connection error
            } finally {
                try {
                    socket.close()
                } catch (ignored: IOException) {
                }
            }
        }

        @Throws(Exception::class)
        private fun handleHome(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val nextPageStr = params["nextPage"]

            if ("ajax" != params["feed"]) {
                val html = HtmlRenderer.renderHomeSkeleton(serviceId, isTv)
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
                return
            }

            try {
                val service = NewPipe.getService(serviceId)
                var items: List<InfoItem>
                // Ready-to-embed "Load More" value - a serialized Page for the (dead) kiosk
                // branch, or HOME_DISCOVERY_LOAD_MORE_TOKEN for YouTube's personalized feed.
                var nextToken: String?

                val feedMode = dbHelper.homeFeedMode
                if (serviceId != SERVICE_YOUTUBE) {
                    // Non-YouTube default kiosk - homeFeedMode ("subs"/"mix") is a YouTube-only
                    // FlowNeuro-fusion preference (see buildAndRankHomeFeed/buildSubsOnlyFeed's own
                    // doc comments), so it's not consulted here; every other service always gets its
                    // own native kiosk feed regardless of that setting.
                    val nextPage = HtmlRenderer.deserializePage(nextPageStr)
                    val page = fetchKioskPage(service, nextPage)
                    items = ArrayList(page.items as List<InfoItem>)
                    nextToken = HtmlRendererCommon.serializePage(page.nextPage)
                } else if ("subs" == feedMode) {
                    // See LocalServerFlowData.kt's buildSubsOnlyFeed().
                    items = dbHelper.buildSubsOnlyFeed(serviceId)
                    nextToken = null
                } else if (nextPageStr == HOME_DISCOVERY_LOAD_MORE_TOKEN) {
                    // "Load More" - see continueDiscoveryFeed() (trending kiosk has no pagination).
                    val (moreItems, hasMore) = dbHelper.continueDiscoveryFeed(serviceId)
                    items = moreItems
                    nextToken = if (hasMore) HOME_DISCOVERY_LOAD_MORE_TOKEN else null
                } else {
                    // See LocalServerFlowData.kt's buildAndRankHomeFeed().
                    val (feedItems, hasMore) = dbHelper.buildAndRankHomeFeed(serviceId, feedMode)
                    items = feedItems
                    nextToken = if (hasMore) HOME_DISCOVERY_LOAD_MORE_TOKEN else null
                }

                val filtered = filterItems(items)
                val html = HtmlRenderer.renderHomeFeed(serviceId, filtered, nextToken)
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
            } catch (e: Exception) {
                // This catch fires for *any* feed failure, not just loss of connectivity, so don't
                // assert the cause - surface the actual exception instead. Claiming "offline" for
                // an extractor bug actively hides the real problem.
                log("Home feed failed: $e")
                val feedSb = StringBuilder()
                feedSb.append("  <div style=\"background-color:#fce8e6; color:#c5221f; padding:16px; border-radius:12px; margin-bottom:24px; font-size:14px; font-weight:500; border: 1px solid #fad2cf;\">\n")
                    .append("    📶 Couldn't load the feed (").append(e.javaClass.simpleName)
                    .append(": ").append(e.message)
                    .append("). You may be offline.\n")
                    .append("  </div>\n")
                sendResponse(os, 200, feedSb.toString(), "text/html; charset=UTF-8")
            }
        }

        // Aggregates recent uploads across every subscribed channel into one reverse-chronological
        // feed, mirroring YouTube's own Subscriptions tab. Each channel is resolved to its own
        // service via getServiceByUrl (not the page's active serviceId) since the subscriptions
        // list mixes YouTube and BiliBili entries. Capped to the newest 60 items so a large
        // subscription list can't stall the page load.
        private fun fetchSubscriptionFeed(channels: List<InfoItem>?): List<InfoItem> {
            var feed = ArrayList<InfoItem>()
            if (channels == null || channels.isEmpty()) {
                return feed
            }
            val futures = ArrayList<Future<List<InfoItem>>>()
            for (channel in channels) {
                val url = channel.url
                // A channel-uploads-tab response usually doesn't include a per-video uploader
                // avatar (that's a channel-level field, not a video-level one), so backfill it
                // from the avatar this app already stored when the user subscribed - otherwise
                // every card in the feed falls back to a plain colored-initial placeholder.
                val subscribedAvatarUrl = channel.thumbnailUrl?.takeIf { it.isNotBlank() }
                futures.add(executorService.submit(Callable {
                    val channelService = NewPipe.getServiceByUrl(url)
                    val uploads = fetchChannelUploads(channelService, url)
                    if (!subscribedAvatarUrl.isNullOrEmpty()) {
                        for (upload in uploads) {
                            if (upload is StreamInfoItem && upload.uploaderAvatarUrl.isNullOrBlank()) {
                                upload.uploaderAvatarUrl = subscribedAvatarUrl
                            }
                        }
                    }
                    uploads
                }))
            }
            for (future in futures) {
                try {
                    val res = future.get(5, TimeUnit.SECONDS)
                    if (res != null) {
                        feed.addAll(res)
                    }
                } catch (e: Exception) {
                    log("Future timeout/error fetching subscription feed: " + e.message)
                }
            }
            feed.sortWith(Comparator { a, b ->
                val dateA = uploadDateOf(a)
                val dateB = uploadDateOf(b)
                if (dateA == null && dateB == null) 0
                else if (dateA == null) 1
                else if (dateB == null) -1
                else dateB.compareTo(dateA)
            })
            if (feed.size > 60) {
                feed = ArrayList(feed.subList(0, 60))
            }
            return feed
        }

        // StreamInfoItem.getUploadDate() is only populated when the extractor provides a precise
        // timestamp in the list response; items without one sort to the end rather than being
        // guessed from the textual "N hours ago" string, which isn't reliably parseable.
        private fun uploadDateOf(item: InfoItem): java.time.OffsetDateTime? {
            if (item is StreamInfoItem) {
                val dw = item.uploadDate
                if (dw != null) {
                    return dw.offsetDateTime()
                }
            }
            return null
        }

        @Throws(Exception::class)
        private fun handleSearch(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val query = params["q"]
            if (query.isNullOrEmpty()) {
                sendRedirect(os, "/?serviceId=$serviceId")
                return
            }
            dbHelper.nativeAddSearchQuery(query)

            val nextPageStr = params["nextPage"]
            val nextPage = HtmlRenderer.deserializePage(nextPageStr)

            try {
                val service = NewPipe.getService(serviceId)
                val extractor = getDefaultSearchExtractor(service, query)

                val page = fetchInitialOrPage(extractor, nextPage)
                val items = page.items
                val next = page.nextPage

                val filtered = filterItems(items)
                val html = HtmlRenderer.renderSearch(serviceId, query, filtered, next, isTv)
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
            } catch (e: Exception) {
                sendResponse(os, 500, "Search failed: ${e.message}", "text/plain; charset=UTF-8")
            }
        }

        // ==================== Fathom<->Flow Stage 2 JSON API (/api/v1/...) ====================
        // Deliberately independent of the HTML handlers just above/below each of these (own
        // extraction calls, not shared helpers) - see ApiRenderer.kt's file header for why.

        private fun handleApiSearch(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val query = params["q"]
            if (query.isNullOrEmpty()) {
                sendResponse(os, 400, ApiRenderer.errorJson("Missing 'q' parameter"), "application/json")
                return
            }
            val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
            try {
                val service = NewPipe.getService(serviceId)
                val extractor = getDefaultSearchExtractor(service, query)
                val page = fetchInitialOrPage(extractor, nextPage)
                val filtered = filterItems(page.items)
                sendResponse(os, 200, ApiRenderer.searchResultJson(filtered, serviceId, page.nextPage).toString(), "application/json")
            } catch (e: Exception) {
                sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
            }
        }

        // Simplified relative to handleHome(): honors homeFeedMode ("subs" -> shuffled uploads
        // from subscribed channels, "mix" -> personalized feed interleaved with subscriptions,
        // default -> personalized-keyword-or-trending search) the same way, but skips the offline
        // cached-video fallback handleHome() renders on failure - a JSON client is expected to
        // handle a 500 itself rather than receive a page-shaped fallback.
        private fun handleApiHome(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
            try {
                val service = NewPipe.getService(serviceId)
                var items: List<InfoItem>
                var next: Page?
                val feedMode = dbHelper.homeFeedMode
                if (serviceId != SERVICE_YOUTUBE) {
                    // Non-YouTube default kiosk - see the same branch in handleHome() for why
                    // homeFeedMode isn't consulted here.
                    val page = fetchKioskPage(service, nextPage)
                    items = ArrayList(page.items as List<InfoItem>)
                    next = page.nextPage
                } else if ("subs" == feedMode) {
                    // See LocalServerFlowData.kt's buildSubsOnlyFeed().
                    items = dbHelper.buildSubsOnlyFeed(serviceId)
                    next = null
                } else {
                    // Real trending kiosk - see fetchTrendingItems() (no pagination available,
                    // so nextPage isn't handled for this branch).
                    items = dbHelper.fetchTrendingItems(serviceId)
                    next = null
                }
                val filtered = filterItems(items)
                sendResponse(os, 200, ApiRenderer.searchResultJson(filtered, serviceId, next).toString(), "application/json")
            } catch (e: Exception) {
                sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
            }
        }

        // New, additive route mirroring handleApiHome() above (same feedMode handling, same
        // response shape via ApiRenderer.searchResultJson()) but sourced through
        // buildAndRankHomeFeed() for the YouTube personalized path, so it comes back already
        // ranked via Flow's real FlowNeuroEngine. handleApiHome() itself is untouched otherwise -
        // this is a separate handler precisely so nothing about its raw/unranked behavior changes.
        private fun handleApiRecommendations(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
            try {
                val service = NewPipe.getService(serviceId)
                var items: List<InfoItem>
                var next: Page?
                var alreadyRanked = false
                val feedMode = dbHelper.homeFeedMode
                if (serviceId != SERVICE_YOUTUBE) {
                    // Non-YouTube default kiosk - see the same branch in handleHome() for why
                    // homeFeedMode isn't consulted here. alreadyRanked stays false so
                    // applyFlowNeuroRanking() below still reorders these by the user's taste.
                    val page = fetchKioskPage(service, nextPage)
                    items = ArrayList(page.items as List<InfoItem>)
                    next = page.nextPage
                } else if ("subs" == feedMode) {
                    // See LocalServerFlowData.kt's buildSubsOnlyFeed().
                    items = dbHelper.buildSubsOnlyFeed(serviceId)
                    next = null
                    alreadyRanked = true
                } else {
                    // Real trending + FlowNeuro discovery + (mix) subscription feed, ranked - see
                    // LocalServerFlowData.kt's buildAndRankHomeFeed(). No pagination available.
                    val (feedItems, _) = dbHelper.buildAndRankHomeFeed(serviceId, feedMode)
                    items = feedItems
                    next = null
                    alreadyRanked = true
                }
                val filtered = filterItems(items)
                val ranked = if (alreadyRanked) filtered else applyFlowNeuroRanking(filtered, serviceId)
                sendResponse(os, 200, ApiRenderer.searchResultJson(ranked, serviceId, next).toString(), "application/json")
            } catch (e: Exception) {
                sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
            }
        }

        private fun handleApiChannel(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val channelUrl = params["id"]
            if (channelUrl.isNullOrEmpty()) {
                sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
                return
            }
            val tab = params.getOrDefault("tab", "videos")
            // "latest"/"popular"/"oldest" - the exact literal values YoutubeChannelTabLinkHandlerFactory's
            // own SORT_LATEST/SORT_POPULAR/SORT_OLDEST constants hold, so this can be passed straight
            // through as a sort-filter string without a lookup table. Only meaningful for YouTube's
            // "videos" tab; harmless no-op everywhere else.
            val sort = params["sort"]?.takeIf { it.isNotBlank() }
            val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
            try {
                val service = NewPipe.getService(serviceId)
                val channelExtractor = service.getChannelExtractor(channelUrl)
                channelExtractor.fetchPage()

                // getChannelTabExtractorFromId(id, tab, baseUrl) (used by the plain
                // else-branch below) hardcodes its sortFilter to "" - it has no way to pass one
                // through - so a non-default sort needs the lower-level construction path built
                // here directly instead. Stock NewPipeExtractor's channel-tab factory has no
                // "search within a channel" tab at all, unlike the fork this was ported from, so
                // that feature is dropped rather than adapted.
                val tabExtractor = if (sort != null && service.channelTabLHFactory != null) {
                    val contentFilter = listOf(FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, tab))
                    val sortFilter = listOf(FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, sort))
                    val linkHandler = service.channelTabLHFactory.fromQuery(
                        channelExtractor.id, contentFilter, sortFilter, channelExtractor.baseUrl)
                    service.getChannelTabExtractor(linkHandler)
                } else {
                    resolveChannelTabExtractor(service, channelExtractor, tab)
                }
                val page = fetchInitialOrPage(tabExtractor, nextPage)
                val items = page.items
                val next = page.nextPage
                backfillUploaderUrl(items, channelUrl)
                val isSubscribed = dbHelper.nativeIsSubscribed(channelExtractor.linkHandler.url)
                val filtered = filterItems(items)

                val json = org.json.JSONObject()
                json.put("channel", ApiRenderer.channelJson(channelExtractor, isSubscribed))
                json.put("videos", ApiRenderer.infoItemsToJson(filtered, serviceId))
                json.put("nextPage", ApiRenderer.serializePageOrNull(next))
                sendResponse(os, 200, json.toString(), "application/json")
            } catch (e: Exception) {
                sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
            }
        }

        private fun handleApiVideo(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val mediaUrl = params["id"]
            if (mediaUrl.isNullOrEmpty()) {
                sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
                return
            }
            try {
                val service = NewPipe.getService(serviceId)
                val extractor = getCachedExtractor(service, serviceId, mediaUrl)
                val info: StreamInfo
                synchronized(extractor) {
                    info = StreamInfo.getInfo(extractor)
                }
                info.relatedItems = dbHelper.nativeRelatedVideos(info, serviceId)
                var thumbUrl = ""
                if (info.thumbnails != null && !info.thumbnails.isEmpty()) {
                    thumbUrl = info.thumbnails[info.thumbnails.size - 1].url
                }
                var uploaderAvatarUrl: String? = null
                if (info.uploaderAvatars != null && !info.uploaderAvatars.isEmpty()) {
                    uploaderAvatarUrl = HtmlRenderer.getThumbnailUrl(info.uploaderAvatars)
                }
                dbHelper.nativeSaveToHistory(info.name, info.url, info.uploaderName, thumbUrl, serviceId, info.uploaderUrl, uploaderAvatarUrl)
                reportFlowNeuroClick(info, serviceId)
                sendResponse(os, 200, ApiRenderer.videoDetailJson(info, serviceId).toString(), "application/json")
            } catch (e: Exception) {
                sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
            }
        }

        private fun handleApiComments(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val videoUrl = params["id"]
            if (videoUrl.isNullOrEmpty()) {
                sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
                return
            }
            val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
            try {
                val service = NewPipe.getService(serviceId)
                val extractor = commentsExtractorFor(service, videoUrl)
                val page = if (nextPage != null) {
                    extractor.getPage(nextPage)
                } else {
                    extractor.fetchPage()
                    if (extractor.isCommentsDisabled) {
                        sendResponse(os, 200, "{\"comments\":[],\"nextPage\":null,\"commentsDisabled\":true}", "application/json")
                        return
                    }
                    extractor.initialPage
                }
                val comments = org.json.JSONArray()
                for (item in page.items) {
                    comments.put(ApiRenderer.commentJson(item))
                }
                val json = org.json.JSONObject()
                json.put("comments", comments)
                json.put("nextPage", ApiRenderer.serializePageOrNull(page.nextPage))
                json.put("commentsDisabled", false)
                sendResponse(os, 200, json.toString(), "application/json")
            } catch (e: Exception) {
                sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
            }
        }

        // Stage 4: lets a client (the Flow fork's new server-address settings screen) confirm it
        // can actually reach this server before anything depends on it, without the cost of a
        // real extraction call - every other /api/v1/... route does real work (search, extractor
        // fetches) that isn't a fair test of plain reachability.
        private fun handleApiPing(os: OutputStream) {
            sendResponse(os, 200, "{\"status\":\"ok\",\"service\":\"fathom\"}", "application/json")
        }

        private fun handleApiWatchProgress(os: OutputStream, params: Map<String, String>) {
            val videoUrl = params["id"]
            if (videoUrl.isNullOrEmpty()) {
                sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
                return
            }
            val percent = params["percent"]?.toIntOrNull()
            val durationSeconds = params["durationSeconds"]?.toIntOrNull() ?: 0
            if (percent == null) {
                sendResponse(os, 400, ApiRenderer.errorJson("Missing or invalid 'percent' parameter"), "application/json")
                return
            }
            dbHelper.nativeUpdateWatchProgress(videoUrl, percent, durationSeconds)

            // FlowNeuro's WATCHED signal - see the "FlowNeuro signal reporting" section below for
            // why this is best-effort. serviceId wasn't previously sent by this endpoint's caller
            // (watchProgressJs in HtmlRendererWatch.kt, updated alongside this); older cached pages
            // still open in a tab won't send it, so this is a no-op (not an error) until reloaded.
            val serviceId = params["serviceId"]?.toIntOrNull()
            if (serviceId != null) {
                try {
                    val service = NewPipe.getService(serviceId)
                    val extractor = getCachedExtractor(service, serviceId, videoUrl)
                    val info: StreamInfo
                    synchronized(extractor) {
                        info = StreamInfo.getInfo(extractor)
                    }
                    dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.WATCHED, percent / 100f)
                } catch (e: Exception) {
                    log("FlowNeuro watch-signal error: " + e.message)
                }
            }

            sendResponse(os, 200, "{\"status\":\"ok\"}", "application/json")
        }

        // ==================== end Stage 2 JSON API ====================

        @Throws(Exception::class)
        private fun handleWatch(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val mediaUrl = params["id"]

            // Immediately send the fast watch skeleton layout
            val html = HtmlRenderer.renderWatchSkeleton(serviceId, mediaUrl, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }

        @Throws(Exception::class)
        private fun handleWatchContent(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val mediaUrl = params["id"]

            try {
                val service = NewPipe.getService(serviceId)
                // Shares one page extraction with handleManifestProxy for this same video,
                // instead of each doing its own independent fetchPage().
                val extractor = getCachedExtractor(service, serviceId, mediaUrl!!)
                // Synchronized since extractor may be concurrently shared with a
                // handleManifestProxy request for the same video (getCachedExtractor above),
                // and StreamInfo.getInfo() calls many extractor getters in bulk here.
                val info: StreamInfo
                synchronized(extractor) {
                    info = StreamInfo.getInfo(extractor)
                }
                info.relatedItems = dbHelper.nativeRelatedVideos(info, serviceId)

                var thumbUrl = ""
                if (info.thumbnails != null && !info.thumbnails.isEmpty()) {
                    thumbUrl = info.thumbnails[info.thumbnails.size - 1].url
                }
                // getThumbnailUrl() always returns a non-null stock-photo URL as its own
                // fallback, so it's only safe to call once we already know a real avatar exists -
                // otherwise that placeholder would get baked permanently into the history row.
                var uploaderAvatarUrl: String? = null
                if (info.uploaderAvatars != null && !info.uploaderAvatars.isEmpty()) {
                    uploaderAvatarUrl = HtmlRenderer.getThumbnailUrl(info.uploaderAvatars)
                }
                dbHelper.nativeSaveToHistory(info.name, info.url, info.uploaderName, thumbUrl, serviceId, info.uploaderUrl, uploaderAvatarUrl)
                reportFlowNeuroClick(info, serviceId)

                val isSubscribed = dbHelper.nativeIsSubscribed(info.uploaderUrl)
                val isWatchLater = dbHelper.nativeIsWatchLater(info.url)
                val likeState = dbHelper.nativeLikeState(info.url)
                val targetQuality = dbHelper.nativeVideoQuality()

                val duration = info.duration
                val html = HtmlRenderer.renderWatchContent(serviceId, info, isSubscribed, isWatchLater, likeState, isTv, targetQuality, duration)
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
            } catch (e: Exception) {
                sendResponse(os, 500, "Error: " + e.message, "text/plain; charset=UTF-8")
            }
        }

        // Fetched by an inline <script> in renderWatchContent()/renderAudioWatch() after the
        // video itself has loaded, rather than blocking the initial /watch-content response on
        // it - comments can be a slow network round-trip and shouldn't delay playback start.
        // Returns a bare HTML fragment (like the "ajax" subscriptions-feed branch), not a full
        // page, since it's injected via innerHTML into an already-rendered page.
        @Throws(Exception::class)
        private fun handleComments(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val videoUrl = params["id"]
            if (videoUrl.isNullOrEmpty()) {
                sendResponse(os, 200, "<div class=\"loading-placeholder\">No video specified.</div>", "text/html; charset=UTF-8")
                return
            }

            val nextPage = HtmlRenderer.deserializePage(params["nextPage"])

            try {
                val service = NewPipe.getService(serviceId)
                val extractor = commentsExtractorFor(service, videoUrl)

                val page = if (nextPage != null) {
                    // YoutubeCommentsExtractor.getPage() only reads the continuation token already
                    // inside nextPage - it doesn't touch anything fetchPage() would have populated,
                    // so it's safe to skip for a fresh extractor with no prior call to rely on.
                    extractor.getPage(nextPage)
                } else {
                    extractor.fetchPage()
                    if (extractor.isCommentsDisabled) {
                        sendResponse(os, 200, "<div class=\"loading-placeholder\">Comments are disabled for this video.</div>", "text/html; charset=UTF-8")
                        return
                    }
                    extractor.initialPage
                }

                val isReplies = params["context"] == "replies"
                val html = HtmlRenderer.renderComments(serviceId, videoUrl, page.items, page.nextPage, isTv, isReplies)
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
            } catch (e: Exception) {
                sendResponse(os, 200, "<div class=\"loading-placeholder\">Failed to load comments: ${e.message}</div>", "text/html; charset=UTF-8")
            }
        }

        // Bilibili danmaku ("bullet comments"). Fetched by an inline <script> in
        // renderWatchContent() after the video itself has loaded, same rationale as
        // handleComments() above - a video can carry thousands of these, so it shouldn't block
        // the initial page. Only Bilibili currently exposes a BulletCommentsExtractor
        // (StreamingService.getBulletCommentsExtractor() returns null otherwise), so this comes
        // back empty for YouTube rather than erroring.
        @Throws(Exception::class)
        private fun handleDanmaku(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val mediaUrl = params["id"]
            if (mediaUrl.isNullOrEmpty()) {
                sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
                return
            }
            try {
                val service = NewPipe.getService(serviceId)
                // BilibiliBulletCommentsExtractor reads the video's cid out of a cache that only
                // the stream extractor's own fetchPage() populates (keyed by video id) - without
                // this, looking it up NPEs. In practice /watch-content already primed this cache
                // for the video the client is currently watching, so this is normally a cache hit.
                getCachedExtractor(service, serviceId, mediaUrl)
                val extractor = service.getBulletCommentsExtractor(mediaUrl)
                if (extractor == null) {
                    sendResponse(os, 200, "{\"danmaku\":[]}", "application/json")
                    return
                }
                extractor.fetchPage()
                if (extractor.isLive) {
                    // Live danmaku needs a persistent connection (WebSocket) this one-shot HTTP
                    // endpoint has no equivalent of - out of scope for now.
                    extractor.disconnect()
                    sendResponse(os, 200, "{\"danmaku\":[]}", "application/json")
                    return
                }
                val danmaku = org.json.JSONArray()
                for (item in extractor.initialPage.items) {
                    danmaku.put(bulletCommentJson(item))
                }
                val json = org.json.JSONObject()
                json.put("danmaku", danmaku)
                sendResponse(os, 200, json.toString(), "application/json")
            } catch (e: Exception) {
                sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
            }
        }

        @Throws(Exception::class)
        private fun handleAudioWatch(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val mediaUrl = params["id"]
            if (mediaUrl.isNullOrEmpty()) {
                sendRedirect(os, "/?serviceId=$serviceId")
                return
            }

            try {
                val service = NewPipe.getService(serviceId)
                // Shares one page extraction with handleManifestProxy for this same video,
                // instead of each doing its own independent fetchPage().
                val extractor = getCachedExtractor(service, serviceId, mediaUrl)
                // Synchronized since extractor may be concurrently shared with a
                // handleManifestProxy request for the same video (getCachedExtractor above),
                // and StreamInfo.getInfo() calls many extractor getters in bulk here.
                val info: StreamInfo
                synchronized(extractor) {
                    info = StreamInfo.getInfo(extractor)
                }
                info.relatedItems = dbHelper.nativeRelatedVideos(info, serviceId)

                var thumbUrl = ""
                if (info.thumbnails != null && !info.thumbnails.isEmpty()) {
                    thumbUrl = info.thumbnails[info.thumbnails.size - 1].url
                }
                // getThumbnailUrl() always returns a non-null stock-photo URL as its own
                // fallback, so it's only safe to call once we already know a real avatar exists -
                // otherwise that placeholder would get baked permanently into the history row.
                var uploaderAvatarUrl: String? = null
                if (info.uploaderAvatars != null && !info.uploaderAvatars.isEmpty()) {
                    uploaderAvatarUrl = HtmlRenderer.getThumbnailUrl(info.uploaderAvatars)
                }
                dbHelper.nativeSaveToHistory(info.name, info.url, info.uploaderName, thumbUrl, serviceId, info.uploaderUrl, uploaderAvatarUrl)
                reportFlowNeuroClick(info, serviceId)

                val isSubscribed = dbHelper.nativeIsSubscribed(info.uploaderUrl)
                val isWatchLater = dbHelper.nativeIsWatchLater(info.url)
                val likeState = dbHelper.nativeLikeState(info.url)
                val html = HtmlRenderer.renderAudioWatch(serviceId, info, isSubscribed, isWatchLater, likeState, isTv)
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
            } catch (e: Exception) {
                sendResponse(os, 500, "Error loading audio stream: " + e.message, "text/plain; charset=UTF-8")
            }
        }

        // Remote native-audio-player control (play/pause/resume/stop) was dropped along with
        // ServerService's own ExoPlayer instance - it duplicated the host app's own player. These
        // four endpoints are kept as graceful no-ops so older clients hitting them don't see a
        // broken request, rather than removing the routes outright.
        @Throws(Exception::class)
        private fun handleApiPlayerPlay(os: OutputStream, params: Map<String, String>) {
            sendResponse(os, 200, "{\"status\":\"unsupported\"}", "application/json")
        }

        @Throws(Exception::class)
        private fun handleApiPlayerPause(os: OutputStream) {
            sendResponse(os, 200, "{\"status\":\"unsupported\"}", "application/json")
        }

        @Throws(Exception::class)
        private fun handleApiPlayerResume(os: OutputStream) {
            sendResponse(os, 200, "{\"status\":\"unsupported\"}", "application/json")
        }

        @Throws(Exception::class)
        private fun handleApiPlayerStop(os: OutputStream) {
            sendResponse(os, 200, "{\"status\":\"unsupported\"}", "application/json")
        }

        @Throws(Exception::class)
        private fun handleHistory(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val items = dbHelper.nativeHistory()
            val html = HtmlRenderer.renderHistory(serviceId, items, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }

        @Throws(Exception::class)
        private fun handleStreamProxy(os: OutputStream, params: Map<String, String>, requestHeaders: Map<String, String>) {
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

            log("STREAM REQUEST itag=$itagParam range=$rangeHeader id=$mediaUrl")

            var requestedItag = -1
            if (itagParam != null) {
                try {
                    requestedItag = itagParam.toInt()
                } catch (e: Exception) {
                }
            }

            val requestedTrackId = params["trackId"]
            // Must be part of the cache key: for itag-less services (Bilibili) the video and audio
            // requests share itag=-1 and trackId, so without mtype they'd collide and the second
            // one would be served the first one's URL.
            val mediaType = params["mtype"]
            val cacheKey = serviceId.toString() + "_" + mediaUrl + "_" + requestedItag +
                    (if (requestedTrackId != null) "_$requestedTrackId" else "") +
                    (if (mediaType != null) "_$mediaType" else "")
            var directUrl = streamUrlCache.get(cacheKey)

            if (directUrl == null) {
                val service = NewPipe.getService(serviceId)
                val extractor = service.getStreamExtractor(mediaUrl)
                extractor.fetchPage()

                // Streams from services without itags (Bilibili reports -1 for everything) can't be
                // picked by itag, so the DASH manifest tags each Representation with mtype and we
                // select the best stream of that kind instead.
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
                    val targetHeight = getResolutionHeight(targetQuality)

                    // Fallback to progressive stream
                    val progressiveStreams = extractor.videoStreams
                    if (progressiveStreams != null && !progressiveStreams.isEmpty()) {
                        var selectedStream: VideoStream? = null
                        var bestHeight = -1
                        for (stream in progressiveStreams) {
                            val height = getResolutionHeight(stream.resolution)
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
                                val height = getResolutionHeight(stream.resolution)
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
                                audioStreams.sortWith(audioTrackPriorityComparator())
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
                    streamUrlCache.put(cacheKey, directUrl, 3600000)
                }
            }

            if (directUrl != null) {
                log("Proxying stream from: $directUrl")

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

                // Bilibili's CDN enforces hotlink protection and returns 403 for any request
                // without a matching Referer. Unlike a browser, this proxy has to set it itself.
                if (directUrl.contains("bilivideo.com") || directUrl.contains("bilibili.com") ||
                    directUrl.contains("akamaized.net")) {
                    reqBuilder.header("Referer", "https://www.bilibili.com/")
                    reqBuilder.header("Origin", "https://www.bilibili.com")
                }

                // Forward Range header if client sent it
                if (rangeHeader != null) {
                    reqBuilder.removeHeader("Range")
                    reqBuilder.addHeader("Range", rangeHeader)
                    log("Forwarding Range to CDN: $rangeHeader")
                }

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val code = response.code
                    log("Incoming Range = $rangeHeader CDN status=$code itag=$requestedItag")

                    if (rangeHeader != null && code != 206) {
                        log("WARNING: Range requested ($rangeHeader) but CDN returned $code")
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
                            // For itag-less services, mtype from the manifest is authoritative;
                            // handing a DASH player application/octet-stream can break playback.
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

                    if (code == 206) log("Successfully returning 206 Partial Content to client")

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
                            log("Stream proxy: Client connection closed.")
                        }
                    }
                    os.flush()
                }
            } else {
                sendResponse(os, 404, "Stream URL not found.", "text/plain; charset=UTF-8")
            }
        }

        @Throws(Exception::class)
        private fun handleManifestProxy(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val mediaUrl = params["id"]!!

            val service = NewPipe.getService(serviceId)
            // Shares one page extraction with handleWatchContent/handleAudioWatch for this same
            // video, instead of each doing its own independent fetchPage().
            val extractor = getCachedExtractor(service, serviceId, mediaUrl)

            // Construct standard DASH manifest (MPD) locally using extracted stream lists.
            // extractor may be shared with a concurrent handleWatchContent/handleAudioWatch
            // request for the same video (getCachedExtractor above) - synchronized on it since
            // the extractor library's getters aren't guaranteed safe to call from two threads
            // at once, unlike the plain-data VideoStream/AudioStream objects they return.
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
                    // Extractors usually return bps. If it's suspiciously low, we might scale,
                    // but the previous scaling (bitrate < 100000) was causing 360p (83kbps)
                    // to be scaled to 83Mbps while 1080p (495kbps) was left at 0.5Mbps.
                    // Let's use a much lower threshold or just trust the extractor.
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

                    // itag is a YouTube concept; Bilibili streams report -1, which would make the
                    // video and audio Representations share an identical BaseURL. mtype gives the
                    // proxy a way to tell them apart when itag can't.
                    val proxyUrl = "/stream?serviceId=" + serviceId + "&amp;id=" + java.net.URLEncoder.encode(mediaUrl, "UTF-8") + "&amp;itag=" + itag + "&amp;mtype=video"
                    // Pre-warm the cache handleStreamProxy checks first, with the exact key it will
                    // compute for this Representation's proxy URL - the player requests this itag
                    // moments after loading this manifest, and without this it would otherwise
                    // redo the full page extraction that was just done to build this manifest.
                    if (vs.content != null) {
                        streamUrlCache.put(serviceId.toString() + "_" + mediaUrl + "_" + itag + "_video", vs.content, 3600000)
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
                    val sorted = audioStreams.sortedWith(audioTrackPriorityComparator())
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

                    // Add Role element based on whether this is the original track. The fork no
                    // longer classifies dub/description/secondary separately (see
                    // isOriginalAudioTrack), so any non-original track is labelled "dub".
                    if (firstStream.audioTrackId != null) {
                        val roleVal = if (isOriginalAudioTrack(firstStream)) "main" else "dub"
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
                        val codec = normalizeAudioCodec(asStream.codec)

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
                            streamUrlCache.put(audioCacheKey, asStream.content, 3600000)
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
            log("Generated local DASH manifest:\n$manifestXml")

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
        private fun handleSubtitlesProxy(os: OutputStream, params: Map<String, String>) {
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
                httpClient.newCall(req).execute().use { response ->
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

        @Throws(Exception::class)
        private fun handleSettings(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val action = params["action"]
            if ("save" == action) {
                if (params.containsKey("video_quality")) {
                    dbHelper.nativeSetVideoQuality(params["video_quality"] ?: "Auto")
                }
                if (params.containsKey("hide_watched")) {
                    dbHelper.nativeSetHideWatched("true" == params["hide_watched"] || "on" == params["hide_watched"])
                }
                if (params.containsKey("hide_shorts")) {
                    dbHelper.nativeSetHideShorts("true" == params["hide_shorts"] || "on" == params["hide_shorts"])
                }
                if (params.containsKey("home_feed_mode")) {
                    dbHelper.homeFeedMode = params["home_feed_mode"] ?: "mix"
                }

                if ("ajax" == params["format"]) {
                    sendResponse(os, 200, "OK", "text/plain; charset=UTF-8")
                    return
                }

                val redirectHeader = "HTTP/1.1 303 See Other\r\n" +
                        "Location: /settings?saved=true\r\n" +
                        "Connection: close\r\n\r\n"
                os.write(redirectHeader.toByteArray(Charsets.UTF_8))
                os.flush()
                return
            }

            val currentQuality = dbHelper.nativeVideoQuality()
            val hideWatched = dbHelper.nativeHideWatched()
            val hideShorts = dbHelper.nativeHideShorts()
            val homeFeedMode = dbHelper.homeFeedMode
            val saved = "true" == params["saved"]

            val html = HtmlRenderer.renderSettings(0, currentQuality, hideWatched, hideShorts, homeFeedMode, saved, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }

        @Throws(Exception::class)
        private fun handleChannel(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val channelUrl = params["id"]!!
            val tab = params.getOrDefault("tab", "videos")

            val nextPageStr = params["nextPage"]
            val nextPage = HtmlRenderer.deserializePage(nextPageStr)

            val service = NewPipe.getService(serviceId)
            val channelExtractor = service.getChannelExtractor(channelUrl)
            channelExtractor.fetchPage()

            val tabExtractor = resolveChannelTabExtractor(
                service, channelExtractor, if ("playlists" == tab) "playlists" else "videos")
            val page = fetchInitialOrPage(tabExtractor, nextPage)
            val items = page.items
            val next = page.nextPage

            backfillUploaderUrl(items, channelUrl)

            val isSubscribed = dbHelper.nativeIsSubscribed(channelExtractor.linkHandler.url)
            if (isSubscribed) {
                try {
                    val cUrl = channelExtractor.linkHandler.url
                    val cName = channelExtractor.name
                    val cAvatar = HtmlRenderer.getThumbnailUrl(channelExtractor.avatars)
                    if (!cAvatar.isNullOrEmpty()) {
                        dbHelper.nativeAddSubscription(cUrl, cName, cAvatar)
                    }
                } catch (ignored: Exception) {
                }
            }
            val isBlocked = dbHelper.nativeIsChannelBlocked(channelExtractor.linkHandler.url)
            val filtered = filterItems(items)
            val html = HtmlRenderer.renderChannel(serviceId, channelExtractor, tab, filtered, next, isSubscribed, isBlocked, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }

        @Throws(Exception::class)
        private fun handlePlaylist(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val playlistUrl = params["id"]!!

            val nextPageStr = params["nextPage"]
            val nextPage = HtmlRenderer.deserializePage(nextPageStr)

            val service = NewPipe.getService(serviceId)
            val extractor = service.getPlaylistExtractor(playlistUrl)

            val page = fetchInitialOrPage(extractor, nextPage)
            val items: List<InfoItem> = page.items
            val next = page.nextPage

            val filtered = filterItems(items)
            val isBookmarked = dbHelper.nativeIsPlaylistBookmarked(playlistUrl)
            val html = HtmlRenderer.renderPlaylist(serviceId, extractor, filtered, next, isBookmarked, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }

        private fun getServiceId(params: Map<String, String>): Int {
            val raw = params["serviceId"]
            if (raw != null) {
                try {
                    val id = raw.trim().toInt()
                    if (isSupportedService(id)) {
                        return id
                    }
                } catch (ignored: NumberFormatException) {
                    // Fall through to the default below.
                }
            }
            return SERVICE_YOUTUBE
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
                    // ignore
                }
            }
            return params
        }

        @Throws(IOException::class)
        private fun sendResponse(os: OutputStream, code: Int, content: String, contentType: String) {
            sendResponse(os, code, content, contentType, null)
        }

        // cacheControl: raw value for the Cache-Control header, or null to omit it (the default
        // for every page/API response, which must never be cached since their content changes
        // per request). Only the externalized static CSS/JS (handleStaticCss/handleStaticJs) pass
        // a real value here.
        @Throws(IOException::class)
        private fun sendResponse(os: OutputStream, code: Int, content: String, contentType: String, cacheControl: String?) {
            var bytes = content.toByteArray(Charsets.UTF_8)
            val status = if (code == 200) "OK" else (if (code == 404) "Not Found" else "Internal Server Error")

            // Most HTML pages still embed a fair amount of inline markup/script even after the
            // shared CSS/JS bundle moved to /static (see HtmlRendererCommon.wrapInTemplate), so
            // gzip is still worthwhile here - and it's what compresses the /static files
            // themselves too. Never applied to video/audio/thumbnail bytes, which are
            // already-compressed media formats gzip wouldn't shrink further. Skipped for tiny
            // bodies (e.g. {"status":"ok"}) since gzip's own header/footer overhead can exceed the
            // savings below a few hundred bytes.
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

            val response = "HTTP/1.1 " + code + " " + status + "\r\n" +
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
        private fun sendRedirect(os: OutputStream, url: String) {
            val response = "HTTP/1.1 302 Found\r\n" +
                    "Location: $url\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n"
            os.write(response.toByteArray(Charsets.UTF_8))
            os.flush()
        }

        @Throws(Exception::class)
        private fun handleSearchHistory(os: OutputStream, params: Map<String, String>) {
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

        @Throws(Exception::class)
        private fun handleSubscriptions(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val activeTab = params.getOrDefault("tab", "feed")

            if ("feed" == activeTab && "ajax" == params["feed"]) {
                val feedItems = fetchSubscriptionFeed(dbHelper.nativeSubscriptions())
                val feedSb = StringBuilder()
                if (feedItems.isEmpty()) {
                    feedSb.append("<div class=\"loading-placeholder\">No recent uploads found from your subscribed channels.</div>\n")
                } else {
                    HtmlRenderer.renderGrid(feedSb, serviceId, feedItems)
                }
                sendResponse(os, 200, feedSb.toString(), "text/html; charset=UTF-8")
                return
            }

            val channels = dbHelper.nativeSubscriptions()
            val playlists = dbHelper.nativeBookmarkedPlaylists()
            val watchLater = dbHelper.nativeWatchLaterItems()
            val html = HtmlRenderer.renderSubscriptions(serviceId, channels, playlists, watchLater, activeTab, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }

        @Throws(Exception::class)
        private fun handleSubscribeAction(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val action = params["action"]
            val channelUrl = params["id"]
            val back = params["back"]

            if ("subscribe" == action && !channelUrl.isNullOrEmpty()) {
                val name = params["name"]
                val avatar = params["avatar"]
                dbHelper.nativeAddSubscription(channelUrl, name, avatar)
            } else if ("unsubscribe" == action && !channelUrl.isNullOrEmpty()) {
                dbHelper.nativeRemoveSubscription(channelUrl)
            }

            if ("ajax" == back) {
                sendResponse(os, 200, "{\"status\":\"success\"}", "application/json")
            } else if (!back.isNullOrEmpty()) {
                if (back.startsWith("/")) {
                    sendRedirect(os, back)
                } else {
                    sendRedirect(os, "/watch?serviceId=" + serviceId + "&id=" + java.net.URLEncoder.encode(back, "UTF-8"))
                }
            } else {
                sendRedirect(os, "/subscriptions?serviceId=$serviceId")
            }
        }

        @Throws(Exception::class)
        private fun handleBlockChannelAction(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val action = params["action"]
            val channelUrl = params["id"]
            val back = params["back"]

            if ("block" == action && !channelUrl.isNullOrEmpty()) {
                dbHelper.nativeBlockChannel(channelUrl)
            } else if ("unblock" == action && !channelUrl.isNullOrEmpty()) {
                dbHelper.nativeUnblockChannel(channelUrl)
            }

            if ("ajax" == back) {
                sendResponse(os, 200, "{\"status\":\"success\"}", "application/json")
            } else if (!back.isNullOrEmpty()) {
                if (back.startsWith("/")) {
                    sendRedirect(os, back)
                } else {
                    sendRedirect(os, "/watch?serviceId=" + serviceId + "&id=" + java.net.URLEncoder.encode(back, "UTF-8"))
                }
            } else {
                sendRedirect(os, "/?serviceId=$serviceId")
            }
        }

        @Throws(Exception::class)
        private fun handlePlaylistBookmarkAction(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val action = params["action"]
            val playlistUrl = params["id"]
            val back = params["back"]

            if ("bookmark" == action && !playlistUrl.isNullOrEmpty()) {
                val name = params["name"]
                dbHelper.nativeBookmarkPlaylist(playlistUrl, name, null)
            } else if ("unbookmark" == action && !playlistUrl.isNullOrEmpty()) {
                dbHelper.nativeUnbookmarkPlaylist(playlistUrl)
            }

            if (!back.isNullOrEmpty()) {
                sendRedirect(os, back)
            } else {
                sendRedirect(os, "/subscriptions?serviceId=$serviceId&tab=playlists")
            }
        }

        // The '?v=' cache-busting param on the request is intentionally ignored here - it only
        // exists so the browser treats a content change as a different URL (see
        // HtmlRendererCommon.STATIC_ASSET_VERSION); the response is the same file regardless of
        // its value. max-age=31536000 (1 year) + immutable is safe specifically because of that:
        // any real edit to HtmlStyles.CSS changes the version tag, which changes the URL, which
        // bypasses this old cache entry entirely rather than serving stale content from it.
        @Throws(Exception::class)
        private fun handleStaticCss(os: OutputStream) {
            sendResponse(os, 200, HtmlStyles.CSS, "text/css; charset=UTF-8", "public, max-age=31536000, immutable")
        }

        @Throws(Exception::class)
        private fun handleStaticJs(os: OutputStream) {
            sendResponse(os, 200, HtmlScripts.RAW_JS, "application/javascript; charset=UTF-8", "public, max-age=31536000, immutable")
        }

        private fun filterItems(items: List<InfoItem>): List<InfoItem> {
            val hideWatched = dbHelper.nativeHideWatched()
            val hideShorts = dbHelper.nativeHideShorts()
            // Flow's native FlowNeuroEngine block list (see nativeBlockedChannelIds()) - its own
            // ranking already excludes these too, so a block here is shared with the native app.
            val blockedChannelIds = dbHelper.nativeBlockedChannelIds()

            val filtered = ArrayList<InfoItem>()
            // getWatchedUrls() only reads the url column instead of hydrating a full
            // StreamInfoItem (title/uploader/thumbnail/avatar) per history row.
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

        // ==================== FlowNeuro signal reporting ====================
        // Without something calling onVideoInteraction(), Flow's brain never learns from Local
        // Server usage and rank() runs permanently cold-start for it. These are the two natural
        // existing hook points - opening a video (handleWatchContent/handleApiVideo, right next
        // to the pre-existing saveToHistory() call) and the watch-progress endpoint - rather than
        // new routes. Both are best-effort and swallow their own exceptions: a FlowNeuro learning
        // failure must never break video playback or the watch-progress endpoint's own DB write.
        private fun reportFlowNeuroClick(info: StreamInfo, serviceId: Int) {
            dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.CLICK)
        }

        // ==================== FlowNeuro ranking ====================
        // Delegates to Flow's real, native FlowNeuroEngine (LocalServerFlowData.kt's
        // rankWithFlowNeuro()) instead of a separately-ported copy. Called from handleHome()'s
        // personalized/mix branch and from handleApiRecommendations() below - deliberately NOT
        // called from handleApiHome() (that handler's existing behavior is left untouched;
        // handleApiRecommendations() is the additive route for ranked results).
        private fun applyFlowNeuroRanking(items: List<InfoItem>, serviceId: Int): List<InfoItem> =
            dbHelper.rankWithFlowNeuro(items, serviceId)

        @Throws(Exception::class)
        private fun handleSendLink(os: OutputStream, params: Map<String, String>, clientIp: String?) {
            val videoUrl = params["id"]
            val clientReleaseCode = params["release_code"]
            var videoTitle = params["title"]
            if (videoTitle.isNullOrEmpty()) {
                videoTitle = "Video"
            }

            if (videoUrl.isNullOrEmpty()) {
                sendResponse(os, 400, "{\"status\":\"error\",\"message\":\"Missing 'id' parameter\"}", "application/json; charset=UTF-8")
                return
            }

            synchronized(LocalHttpServer::class.java) {
                var hasLock = false
                var currentLockCode = getActiveLockCode()

                if (currentLockCode == null) {
                    val newLockCode = UUID.randomUUID().toString()
                    tryLock(newLockCode, clientIp ?: "", videoTitle)
                    currentLockCode = newLockCode
                    hasLock = true
                } else if (currentLockCode == clientReleaseCode) {
                    tryLock(currentLockCode, clientIp ?: "", videoTitle)
                    hasLock = true
                }

                if (hasLock) {
                    // "__connect_only__" is sent by the header's cast button when the current page
                    // isn't a video (e.g. Home, Subscriptions) - it pairs this device as remote-
                    // controllable without queuing a bogus play_video command for a non-video URL,
                    // which used to make the connecting browser navigate itself to a broken /watch
                    // link (see RemoteActivity's touchpad flow: pairing must work from any page).
                    if ("__connect_only__" != videoUrl) {
                        log("Casting link: $videoUrl from client IP $clientIp")
                        addPendingCommand("play_video:$videoUrl")
                    } else {
                        log("Remote connected (pairing only) from client IP $clientIp")
                    }

                    val json = "{\"status\":\"success\",\"release_code\":\"" + currentLockCode + "\"}"
                    sendResponse(os, 200, json, "application/json; charset=UTF-8")
                } else {
                    var busyMsg = "Server is currently controlled by device at IP " + getActiveClientIp()
                    if (getActiveVideoTitle() != null) {
                        busyMsg += " playing: " + getActiveVideoTitle()
                    }
                    val json = "{\"status\":\"busy\",\"message\":\"" + busyMsg.replace("\"", "\\\"") + "\"}"
                    sendResponse(os, 200, json, "application/json; charset=UTF-8")
                }
            }
        }

        @Throws(Exception::class)
        private fun handleReleaseLock(os: OutputStream, params: Map<String, String>) {
            val clientReleaseCode = params["release_code"]
            if (clientReleaseCode.isNullOrEmpty()) {
                sendResponse(os, 400, "{\"status\":\"error\",\"message\":\"Missing 'release_code' parameter\"}", "application/json; charset=UTF-8")
                return
            }

            synchronized(LocalHttpServer::class.java) {
                val currentLockCode = getActiveLockCode()
                if (currentLockCode != null && currentLockCode == clientReleaseCode) {
                    releaseLock()
                    log("Lock released by client.")
                    sendResponse(os, 200, "{\"status\":\"success\"}", "application/json; charset=UTF-8")
                } else {
                    sendResponse(os, 200, "{\"status\":\"error\",\"message\":\"Invalid or expired lock code\"}", "application/json; charset=UTF-8")
                }
            }
        }

        @Throws(Exception::class)
        private fun handleSendCommand(os: OutputStream, params: Map<String, String>) {
            val cmd = params["command"]
            val clientReleaseCode = params["release_code"]
            if (cmd.isNullOrEmpty()) {
                sendResponse(os, 400, "{\"status\":\"error\",\"message\":\"Missing 'command' parameter\"}", "application/json; charset=UTF-8")
                return
            }

            synchronized(LocalHttpServer::class.java) {
                val currentLockCode = getActiveLockCode()
                if (currentLockCode != null && currentLockCode == clientReleaseCode) {
                    addPendingCommand(cmd)
                    sendResponse(os, 200, "{\"status\":\"success\"}", "application/json; charset=UTF-8")
                } else {
                    sendResponse(os, 200, "{\"status\":\"error\",\"message\":\"Not authorized / lock expired\"}", "application/json; charset=UTF-8")
                }
            }
        }

        @Throws(Exception::class)
        private fun handlePollCommands(os: OutputStream) {
            val cmds = getAndClearPendingCommands()
            val sb = StringBuilder()
            sb.append("{\"commands\":[")
            for (i in cmds.indices) {
                sb.append("\"").append(cmds[i].replace("\"", "\\\"")).append("\"")
                if (i < cmds.size - 1) {
                    sb.append(",")
                }
            }
            sb.append("]}")
            sendResponse(os, 200, sb.toString(), "application/json; charset=UTF-8")
        }

        @Throws(Exception::class)
        private fun handleWatchLater(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val items = dbHelper.nativeWatchLaterItems()
            val html = HtmlRenderer.renderWatchLater(serviceId, items, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }

        @Throws(Exception::class)
        private fun handleHistoryAction(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val action = params["action"]
            val url = params["url"]
            val back = params["back"]

            if ("remove" == action && !url.isNullOrEmpty()) {
                dbHelper.nativeRemoveFromHistory(url)
            } else if ("clear" == action) {
                dbHelper.nativeClearHistory()
            }

            if ("ajax" == back) {
                sendResponse(os, 200, "{\"status\":\"success\"}", "application/json")
            } else {
                sendRedirect(os, "/history?serviceId=$serviceId")
            }
        }

        private fun handleWatchLaterAction(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val action = params["action"]
            val url = params["url"]
            val back = params["back"]

            if ("add" == action && !url.isNullOrEmpty()) {
                val title = params["title"].takeUnless { it.isNullOrEmpty() } ?: "Shared Item"
                val uploader = params["uploader"] ?: ""
                val thumbnail = params["thumbnail"]
                val uploaderUrl = params["uploaderUrl"]
                dbHelper.nativeAddWatchLater(url, title, uploader, thumbnail, uploaderUrl)
            } else if ("remove" == action && !url.isNullOrEmpty()) {
                dbHelper.nativeRemoveWatchLater(url)
            }

            if ("ajax" == back) {
                sendResponse(os, 200, "{\"status\":\"success\"}", "application/json")
            } else if (!back.isNullOrEmpty()) {
                if (back.startsWith("/")) {
                    sendRedirect(os, back)
                } else {
                    sendRedirect(os, "/watch?serviceId=" + serviceId + "&id=" + java.net.URLEncoder.encode(back, "UTF-8"))
                }
            } else {
                sendRedirect(os, "/watch-later?serviceId=$serviceId")
            }
        }

        private fun handleRateVideoAction(os: OutputStream, params: Map<String, String>) {
            val serviceId = getServiceId(params)
            val action = params["action"]
            val url = params["url"]
            val back = params["back"]

            if (!url.isNullOrEmpty()) {
                val title = params["title"].takeUnless { it.isNullOrEmpty() } ?: "Shared Item"
                val uploader = params["uploader"] ?: ""
                val thumbnail = params["thumbnail"]
                val uploaderUrl = params["uploaderUrl"]
                when (action) {
                    "like" -> dbHelper.nativeLikeVideo(url, title, uploader, thumbnail, uploaderUrl, serviceId)
                    "dislike" -> dbHelper.nativeDislikeVideo(url, title, uploader, thumbnail, uploaderUrl)
                    "remove" -> dbHelper.nativeRemoveLikeState(url)
                }
            }

            if ("ajax" == back) {
                sendResponse(os, 200, "{\"status\":\"success\"}", "application/json")
            } else if (!back.isNullOrEmpty()) {
                if (back.startsWith("/")) {
                    sendRedirect(os, back)
                } else {
                    sendRedirect(os, "/watch?serviceId=" + serviceId + "&id=" + java.net.URLEncoder.encode(back, "UTF-8"))
                }
            } else {
                sendRedirect(os, "/?serviceId=$serviceId")
            }
        }

        private fun escapeJson(input: String?): String {
            if (input == null) return ""
            return input.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        }
    }

    private class CacheData(val value: String?, timeoutMillis: Long) {
        val expireTimestamp: Long = System.currentTimeMillis() + timeoutMillis

        fun isExpired(): Boolean {
            return System.currentTimeMillis() > expireTimestamp
        }
    }

    private class StreamUrlCache {
        companion object {
            private const val MAX_ITEMS = 60
        }

        private val map = object : LinkedHashMap<String, CacheData>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheData>?): Boolean {
                return size > MAX_ITEMS
            }
        }

        @Synchronized
        fun get(key: String): String? {
            val data = map[key]
            if (data == null) {
                return null
            }
            if (data.isExpired()) {
                map.remove(key)
                return null
            }
            return data.value
        }

        @Synchronized
        fun put(key: String, value: String?, timeoutMillis: Long) {
            removeStale()
            map[key] = CacheData(value, timeoutMillis)
        }

        private fun removeStale() {
            map.entries.removeIf { it.value.isExpired() }
        }

        @Synchronized
        fun clear() {
            map.clear()
        }
    }

    private class ExtractorCacheData(val value: StreamExtractor, timeoutMillis: Long) {
        val expireTimestamp: Long = System.currentTimeMillis() + timeoutMillis

        fun isExpired(): Boolean {
            return System.currentTimeMillis() > expireTimestamp
        }
    }

    private class ExtractorCache {
        companion object {
            private const val MAX_ITEMS = 15
        }

        private val map = object : LinkedHashMap<String, ExtractorCacheData>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExtractorCacheData>?): Boolean {
                return size > MAX_ITEMS
            }
        }

        @Synchronized
        fun get(key: String): StreamExtractor? {
            val data = map[key]
            if (data == null) {
                return null
            }
            if (data.isExpired()) {
                map.remove(key)
                return null
            }
            return data.value
        }

        @Synchronized
        fun put(key: String, value: StreamExtractor, timeoutMillis: Long) {
            map.entries.removeIf { it.value.isExpired() }
            map[key] = ExtractorCacheData(value, timeoutMillis)
        }
    }
}
