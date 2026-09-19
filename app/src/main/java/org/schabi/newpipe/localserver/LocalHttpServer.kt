package org.schabi.newpipe.localserver

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.MediaFormat
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
        internal val streamUrlCache = StreamUrlCache()

        // Shared by handleWatchContent/handleAudioWatch (StreamInfo) and handleManifestProxy
        // (raw stream lists) - one page extraction per video, not two. Smaller than streamUrlCache
        // since entries hold parsed extractor state, not just a URL string.
        private val extractorCache = ExtractorCache()
        internal val httpClient: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
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

        // BilibiliChannelInfoItemWebAPIExtractor/ClientAPIExtractor never override getUploaderUrl(),
        // falling through to the "" default - an empty uploader link 404s the channel route. Every
        // item here already belongs to channelUrl, so backfill it directly.
        internal fun backfillUploaderUrl(items: List<InfoItem>, channelUrl: String) {
            for (item in items) {
                if (item is StreamInfoItem) {
                    val currentUploaderUrl = item.uploaderUrl
                    if (currentUploaderUrl == null || currentUploaderUrl.trim().isEmpty()) {
                        item.setUploaderUrl(channelUrl)
                    }
                }
            }
        }

        // getChannelTabExtractorFromId() calls getChannelTabLHFactory().fromQuery() unconditionally,
        // NPEing for a service with no query factory (Bilibili) - falls back to matching by
        // ChannelTabs FilterItem name off getTabs() instead.
        internal fun resolveChannelTabExtractor(
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

        // Common "resume from nextPage token, else fetch first page" shape for search/channel-tab/
        // playlist extractors - delegates to PPE's shared ListExtractorCompat.
        internal fun <R : InfoItem> fetchInitialOrPage(extractor: ListExtractor<R>, nextPage: Page?): InfoItemsPage<R> =
            org.schabi.newpipe.extractor.compat.ListExtractorCompat.fetchInitialOrPage(extractor, nextPage)

        // Manual fetchInitialOrPage() equivalent: getDefaultKioskExtractor() returns a raw
        // KioskExtractor, incompatible with the generic version's type param. Shared by
        // handleHome/handleApiHome/handleApiRecommendations's non-YouTube branch (no personalized
        // feed outside YouTube - always native trending kiosk, regardless of homeFeedMode).
        internal fun fetchKioskPage(service: StreamingService, nextPage: Page?): InfoItemsPage<*> {
            val kioskExtractor = service.kioskList.defaultKioskExtractor
            kioskExtractor.fetchPage()
            return if (nextPage != null) kioskExtractor.getPage(nextPage) else kioskExtractor.initialPage
        }

        // Bilibili bangumi urls: comments need the episode's bvid from WatchDataCache, populated by
        // the stream extractor. BilibiliCommentsCompat avoids an NPE when /comments races
        // /watch-content; a no-op for non-bangumi urls.
        internal fun commentsExtractorFor(service: StreamingService, videoUrl: String) =
            if (service is org.schabi.newpipe.extractor.services.bilibili.BilibiliService) {
                org.schabi.newpipe.extractor.services.bilibili.compat.BilibiliCommentsCompat
                    .getCommentsExtractor(service, videoUrl)
            } else {
                service.getCommentsExtractor(videoUrl)
            }

        // BulletCommentsInfoItem.getLastingTime() always returns -1 (extractor bug) - on-screen
        // duration is hardcoded client-side instead, not serialized here.
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
         * Search extractor with the default "all" content filter. An empty filter list is not a
         * safe substitute: `YoutubeFilters.evaluateSelectedFilters()` throws on it.
         */
        @JvmStatic
        @Throws(ExtractionException::class)
        fun getDefaultSearchExtractor(service: StreamingService, query: String): SearchExtractor {
            // BilibiliFilters has no "all" filter - resolveSearchContentFilters() falls back to
            // Bilibili's "videos" default instead of silently going filterless (which drops the
            // required search_type= param and breaks BilibiliSearchExtractor's response parsing).
            val defaultFilter =
                SearchFilterResolver.resolveSearchContentFilters(
                    service,
                    listOf(SearchFilterResolver.DEFAULT_CONTENT_FILTER_NAME),
                )
            return service.getSearchExtractor(query, defaultFilter, emptyList())
        }

        /**
         * Normalizes an audio codec string for a DASH manifest. Bilibili reports bare `"mp4a"`,
         * an invalid RFC 6381 string that fails `MediaSource.isTypeSupported()` and drops the
         * audio Representation - maps to AAC-LC (`mp4a.40.2`), what Bilibili actually serves.
         * YouTube's already-qualified strings pass through untouched.
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

        // Default audio-track priority: original track, then device-locale match, then English,
        // then highest bitrate. Shared across the stream proxy, DASH manifest generator, and
        // HtmlRenderer's UI track picker - keeps "default" consistent with what's actually served.
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

        // The DASH manifest (handleManifestProxy) only ever draws its video AdaptationSet from
        // video-ONLY streams that are MPEG_4 and carry valid SegmentBase index/init ranges -
        // progressive streams and other formats (e.g. WebM/VP9) never appear in it. The quality
        // dropdown (HtmlRendererWatch.kt) must offer exactly this same set, or a selection that
        // doesn't exist in the manifest silently matches no QualityLevel and does nothing -
        // exactly the "quality selection doesn't work" symptom this was written to fix.
        @JvmStatic
        fun dashPlayableVideoOnlyStreams(videoOnlyStreams: List<VideoStream>?): List<VideoStream> {
            if (videoOnlyStreams.isNullOrEmpty()) return emptyList()
            val seenItags = HashSet<Int>()
            return videoOnlyStreams.filter { vs ->
                vs.format == MediaFormat.MPEG_4 &&
                    vs.initStart >= 0 && vs.initEnd >= 0 && vs.indexStart >= 0 && vs.indexEnd >= 0 &&
                    seenItags.add(vs.itag)
            }
        }

        // Shared by handleWatchContent/handleAudioWatch/handleManifestProxy - one
        // getStreamExtractor()+fetchPage() per video, regardless of which handler runs first.
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

    // internal, not private: handler groups live as extension functions in sibling files
    // (LocalHttpServerActionHandlers.kt et al.). dbHelper/sendResponse/sendRedirect/getServiceId
    // below are internal for the same reason.
    internal class ClientHandler(
        private val socket: Socket,
        internal val dbHelper: HistoryDbHelper,
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
                            // Route table, not an if-else chain. Rebuilt per request: each lambda
                            // closes over this request's os/params/isTv/requestHeaders; negligible
                            // allocation at LAN-server request volume.
                            val routes: Map<String, () -> Unit> = mapOf(
                                "/" to { handleHome(os, params, isTv) },
                                "/search" to { handleSearch(os, params, isTv) },
                                "/watch" to { handleWatch(os, params, isTv) },
                                "/audio" to { handleAudioWatch(os, params, isTv) },
                                "/watch-content" to { handleWatchContent(os, params, isTv) },
                                "/comments" to { handleComments(os, params, isTv) },
                                "/danmaku" to { handleDanmaku(os, params) },
                                "/send-link" to { handleSendLink(os, params, socket.inetAddress.hostAddress) },
                                "/play" to { handleSendLink(os, params, socket.inetAddress.hostAddress) },
                                "/send-command" to { handleSendCommand(os, params) },
                                "/poll-commands" to { handlePollCommands(os) },
                                "/release-lock" to { handleReleaseLock(os, params) },
                                "/history" to { handleHistory(os, params, isTv) },
                                "/history_action" to { handleHistoryAction(os, params) },
                                "/channel" to { handleChannel(os, params, isTv) },
                                "/playlist" to { handlePlaylist(os, params, isTv) },
                                "/stream" to { handleStreamProxy(os, params, requestHeaders) },
                                "/manifest" to { handleManifestProxy(os, params) },
                                "/subtitles" to { handleSubtitlesProxy(os, params) },
                                "/log_client_capabilities" to { handleLogClientCapabilities(os, params, requestHeaders) },
                                "/api/player/play" to { handleApiPlayerPlay(os, params) },
                                "/api/player/pause" to { handleApiPlayerPause(os) },
                                "/api/player/resume" to { handleApiPlayerResume(os) },
                                "/api/player/stop" to { handleApiPlayerStop(os) },
                                "/search-history" to { handleSearchHistory(os, params) },
                                "/subscriptions" to { handleSubscriptions(os, params, isTv) },
                                "/subscribe" to { handleSubscribeAction(os, params) },
                                "/block_channel" to { handleBlockChannelAction(os, params) },
                                "/bookmark_playlist" to { handlePlaylistBookmarkAction(os, params) },
                                "/static/style.css" to { handleStaticCss(os) },
                                "/static/script.js" to { handleStaticJs(os) },
                                "/settings" to { handleSettings(os, params, isTv) },
                                "/watch-later" to { handleWatchLater(os, params, isTv) },
                                "/watch_later_action" to { handleWatchLaterAction(os, params) },
                                "/rate_video" to { handleRateVideoAction(os, params) },
                                "/api/v1/search" to { handleApiSearch(os, params) },
                                "/api/v1/home" to { handleApiHome(os, params) },
                                "/api/v1/channel" to { handleApiChannel(os, params) },
                                "/api/v1/video" to { handleApiVideo(os, params) },
                                "/api/v1/comments" to { handleApiComments(os, params) },
                                "/api/v1/watch_progress" to { handleApiWatchProgress(os, params) },
                                "/api/v1/download" to { handleApiDownload(os, params) },
                                "/api/v1/bilibili_probe" to { handleApiBilibiliProbe(os, params) },
                                "/api/v1/recommendations" to { handleApiRecommendations(os, params) },
                                "/api/v1/ping" to { handleApiPing(os) },
                            )
                            val route = routes[path]
                            if (route != null) {
                                route()
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
                    // homeFeedMode ("subs"/"mix") is YouTube-only (see buildAndRankHomeFeed/
                    // buildSubsOnlyFeed) - other services always get their native kiosk feed.
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
                // Fires for any feed failure, not just connectivity loss - surface the actual
                // exception rather than assuming "offline".
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

        // Aggregates subscribed-channel uploads into one reverse-chronological feed (Subscriptions
        // tab). Resolves each channel via getServiceByUrl, not the page's serviceId - the
        // subscriptions list mixes YouTube/Bilibili. Capped to 60 items.
        private fun fetchSubscriptionFeed(channels: List<InfoItem>?): List<InfoItem> {
            var feed = ArrayList<InfoItem>()
            if (channels == null || channels.isEmpty()) {
                return feed
            }
            val futures = ArrayList<Future<List<InfoItem>>>()
            for (channel in channels) {
                val url = channel.url
                // Channel-uploads-tab items carry no per-video avatar - backfill from the
                // subscription's stored avatar, else cards fall back to a placeholder.
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

        // getUploadDate() is null unless the extractor provides a precise timestamp; unparseable
        // "N hours ago" text is never guessed from - items without one sort last.
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
            // loadMoreSearch()'s ajax=1 follow-ups are a continuation of this same search, not a
            // new one - only the initial page load should add a search-history entry.
            val isAjax = params["ajax"] == "1"
            if (!isAjax) {
                dbHelper.nativeAddSearchQuery(query)
            }

            val nextPageStr = params["nextPage"]
            val nextPage = HtmlRenderer.deserializePage(nextPageStr)

            try {
                val service = NewPipe.getService(serviceId)
                val extractor = getDefaultSearchExtractor(service, query)

                val page = fetchInitialOrPage(extractor, nextPage)
                val items = page.items
                val next = page.nextPage

                val filtered = filterItems(items)
                val html =
                    if (isAjax) {
                        HtmlRenderer.renderSearchResultsFragment(serviceId, query, filtered, next)
                    } else {
                        HtmlRenderer.renderSearch(serviceId, query, filtered, next, isTv)
                    }
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
            } catch (e: Exception) {
                sendResponse(os, 500, "Search failed: ${e.message}", "text/plain; charset=UTF-8")
            }
        }

        // /api/v1/... JSON API handlers live in LocalHttpServerApiHandlers.kt - independent of the
        // HTML handlers here (see ApiRenderer.kt's header).

        // Video/audio watch-page content, comments, and danmaku handlers live in
        // LocalHttpServerWatchHandlers.kt.

        @Throws(Exception::class)
        private fun handleHistory(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val items = dbHelper.nativeHistory()
            val html = HtmlRenderer.renderHistory(serviceId, items, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }

        // Stream/manifest/subtitles proxy handlers live in LocalHttpServerProxyHandlers.kt.

        private fun handleLogClientCapabilities(os: OutputStream, params: Map<String, String>, requestHeaders: Map<String, String>) {
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

        private fun handleChannel(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val channelUrl = params["id"]!!
            val tab = params.getOrDefault("tab", "videos")
            // loadMoreChannel()'s ajax=1 follow-ups only need the next batch of the grid, not the
            // channel header/subscribe-button chrome around it.
            val isAjax = params["ajax"] == "1"

            val nextPageStr = params["nextPage"]
            val nextPage = HtmlRenderer.deserializePage(nextPageStr)

            try {
                val service = NewPipe.getService(serviceId)
                val channelExtractor = service.getChannelExtractor(channelUrl)
                channelExtractor.fetchPage()

                val tabExtractor = resolveChannelTabExtractor(
                    service, channelExtractor, if ("playlists" == tab) "playlists" else "videos")
                val page = fetchInitialOrPage(tabExtractor, nextPage)
                val items = page.items
                val next = page.nextPage

                backfillUploaderUrl(items, channelUrl)
                val filtered = filterItems(items)

                if (isAjax) {
                    val channelAvatar = HtmlRenderer.getThumbnailUrl(channelExtractor.avatars)
                    val channelAvatarFallback = if (HtmlRendererCommon.hasThumbnail(channelExtractor.avatars)) channelAvatar else null
                    val html = HtmlRenderer.renderChannelItemsFragment(serviceId, channelUrl, tab, filtered, next, channelAvatarFallback)
                    sendResponse(os, 200, html, "text/html; charset=UTF-8")
                    return
                }

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
                val html = HtmlRenderer.renderChannel(serviceId, channelExtractor, tab, filtered, next, isSubscribed, isBlocked, isTv)
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
            } catch (e: Exception) {
                sendResponse(os, 500, "Channel failed: ${e.message}", "text/plain; charset=UTF-8")
            }
        }

        private fun handlePlaylist(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val playlistUrl = params["id"]!!
            // loadMorePlaylist()'s ajax=1 follow-ups only need the next batch of the grid, not the
            // playlist header/bookmark-button chrome around it.
            val isAjax = params["ajax"] == "1"

            val nextPageStr = params["nextPage"]
            val nextPage = HtmlRenderer.deserializePage(nextPageStr)

            try {
                val service = NewPipe.getService(serviceId)
                val extractor = service.getPlaylistExtractor(playlistUrl)

                val page = fetchInitialOrPage(extractor, nextPage)
                val items: List<InfoItem> = page.items
                val next = page.nextPage

                val filtered = filterItems(items)

                if (isAjax) {
                    val html = HtmlRenderer.renderPlaylistItemsFragment(serviceId, playlistUrl, filtered, next)
                    sendResponse(os, 200, html, "text/html; charset=UTF-8")
                    return
                }

                val isBookmarked = dbHelper.nativeIsPlaylistBookmarked(playlistUrl)
                val html = HtmlRenderer.renderPlaylist(serviceId, extractor, filtered, next, isBookmarked, isTv)
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
            } catch (e: Exception) {
                sendResponse(os, 500, "Playlist failed: ${e.message}", "text/plain; charset=UTF-8")
            }
        }

        internal fun getServiceId(params: Map<String, String>): Int {
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
        internal fun sendResponse(os: OutputStream, code: Int, content: String, contentType: String) {
            sendResponse(os, code, content, contentType, null)
        }

        // cacheControl: Cache-Control header value, or null to omit (default for all page/API
        // responses). Only handleStaticCss/handleStaticJs pass a real value.
        @Throws(IOException::class)
        internal fun sendResponse(os: OutputStream, code: Int, content: String, contentType: String, cacheControl: String?) {
            var bytes = content.toByteArray(Charsets.UTF_8)
            val status = if (code == 200) "OK" else (if (code == 404) "Not Found" else "Internal Server Error")

            // gzip: worthwhile for HTML (still carries inline markup/script) and /static files;
            // never applied to media bytes (already compressed); skipped below 512B where
            // header/footer overhead exceeds the savings.
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
        internal fun sendRedirect(os: OutputStream, url: String) {
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
                try {
                    val feedItems = fetchSubscriptionFeed(dbHelper.nativeSubscriptions())
                    val feedSb = StringBuilder()
                    if (feedItems.isEmpty()) {
                        feedSb.append("<div class=\"loading-placeholder\">No recent uploads found from your subscribed channels.</div>\n")
                    } else {
                        HtmlRenderer.renderGrid(feedSb, serviceId, feedItems)
                    }
                    sendResponse(os, 200, feedSb.toString(), "text/html; charset=UTF-8")
                } catch (e: Exception) {
                    sendResponse(os, 500, "Subscription feed failed: ${e.message}", "text/plain; charset=UTF-8")
                }
                return
            }

            val channels = dbHelper.nativeSubscriptions()
            val playlists = dbHelper.nativeBookmarkedPlaylists()
            val watchLater = dbHelper.nativeWatchLaterItems()
            val html = HtmlRenderer.renderSubscriptions(serviceId, channels, playlists, watchLater, activeTab, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }

        internal fun filterItems(items: List<InfoItem>): List<InfoItem> {
            val hideWatched = dbHelper.nativeHideWatched()
            val hideShorts = dbHelper.nativeHideShorts()
            // FlowNeuroEngine's block list - shared with native ranking.
            val blockedChannelIds = dbHelper.nativeBlockedChannelIds()

            val filtered = ArrayList<InfoItem>()
            // URL-only read, no full StreamInfoItem hydration per history row.
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
        // Hooked at handleWatchContent/handleApiVideo (next to saveToHistory()) and the
        // watch-progress endpoint, not new routes. Best-effort: exceptions never break playback
        // or the endpoint's own DB write.
        internal fun reportFlowNeuroClick(info: StreamInfo, serviceId: Int) {
            dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.CLICK)
        }

        // ==================== FlowNeuro ranking ====================
        // Delegates to native FlowNeuroEngine (rankWithFlowNeuro()), not a ported copy. Used by
        // handleHome()'s mix branch and handleApiRecommendations() - NOT handleApiHome(), whose
        // existing behavior stays untouched.
        internal fun applyFlowNeuroRanking(items: List<InfoItem>, serviceId: Int): List<InfoItem> =
            dbHelper.rankWithFlowNeuro(items, serviceId)

        @Throws(Exception::class)
        private fun handleWatchLater(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val items = dbHelper.nativeWatchLaterItems()
            val html = HtmlRenderer.renderWatchLater(serviceId, items, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
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

    internal class StreamUrlCache {
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
