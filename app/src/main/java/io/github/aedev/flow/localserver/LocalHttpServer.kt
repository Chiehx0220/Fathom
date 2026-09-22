package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliLink
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream

import io.github.aedev.flow.data.recommendation.InteractionType

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class LocalHttpServer(private val context: android.content.Context, private val port: Int) {

    init {
        WebAssets.init(context)
    }

    /** Which page, if any, has paired with the server to be remote-controlled. */
    data class RemoteLock(
        val locked: Boolean,
        val clientIp: String?,
        val title: String?,
    )

    /** What the paired page last reported: whether a video is open and how it is playing. */
    data class RemoteState(
        val watching: Boolean = false,
        val title: String? = null,
        val positionSec: Double = 0.0,
        val durationSec: Double = 0.0,
        val paused: Boolean = true,
        val volume: Float = 1f,
        val muted: Boolean = false,
        val fullscreen: Boolean = false,
        /** Name of the chapter being played and how many the video has (0 when it has none). */
        val chapter: String? = null,
        val chapterCount: Int = 0,
        /** The sources the page can switch between (id to name) and which one it is on. */
        val services: List<Pair<Int, String>> = emptyList(),
        val activeService: Int? = null,
    )

    companion object {
        private val remoteLockState = MutableStateFlow(RemoteLock(false, null, null))
        val remoteLock: StateFlow<RemoteLock> = remoteLockState.asStateFlow()

        private val remoteStateFlow = MutableStateFlow(RemoteState())
        val remoteState: StateFlow<RemoteState> = remoteStateFlow.asStateFlow()

        @JvmStatic
        fun updateRemoteState(state: RemoteState) {
            remoteStateFlow.value = state
        }

        @Volatile
        private var activeLockCode: String? = null
        @Volatile
        private var activeClientIp: String? = null
        @Volatile
        private var activeVideoTitle: String? = null
        @Volatile
        private var wsServer: RemoteWebSocketServer? = null
        private val pendingCommands = java.util.concurrent.LinkedBlockingQueue<String>()

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
            remoteLockState.value = RemoteLock(false, null, null)
            remoteStateFlow.value = RemoteState()
        }

        @JvmStatic
        fun tryLock(code: String, clientIp: String, title: String?): Boolean {
            val currentLockCode = activeLockCode
            if (currentLockCode == null) {
                activeLockCode = code
                activeClientIp = clientIp
                activeVideoTitle = title
                remoteLockState.value = RemoteLock(true, clientIp, title)
                return true
            } else if (currentLockCode == code) {
                activeVideoTitle = title
                remoteLockState.value = RemoteLock(true, activeClientIp, title)
                return true
            }
            return false
        }

        internal val streamUrlCache = StreamUrlCache()

        // One extraction per video, shared by the watch and manifest handlers. Smaller than streamUrlCache: entries hold parsed extractor state.
        private val extractorCache = ExtractorCache()
        internal val httpClient: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        @JvmStatic
        fun log(message: String) {
            android.util.Log.d("LocalServer", message)
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
                val tabExtractor = service.getChannelTabExtractor(resolveChannelTabHandler(channelExtractor, "videos"))
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

        // Backfill the uploader link: an empty one 404s the channel route, and every item here already belongs to channelUrl.
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

        // The channel's handler for [tab] ("videos", "playlists", ...), else its first tab.
        internal fun resolveChannelTabHandler(
            channelExtractor: ChannelExtractor,
            tab: String,
        ): ListLinkHandler =
            channelExtractor.tabs.firstOrNull { handler -> handler.contentFilters.contains(tab) }
                ?: channelExtractor.tabs.firstOrNull()
                ?: throw ExtractionException("Channel exposes no tabs: ${channelExtractor.url}")

        // Resume from the nextPage token, else fetch the first page. getPage() reads only the token, so a fresh extractor can resume.
        internal fun <R : InfoItem> fetchInitialOrPage(extractor: ListExtractor<R>, nextPage: Page?): InfoItemsPage<R> {
            extractor.fetchPage()
            return if (nextPage != null) extractor.getPage(nextPage) else extractor.initialPage
        }

        const val SERVICE_YOUTUBE = 0
        val SUPPORTED_SERVICE_IDS = intArrayOf(SERVICE_YOUTUBE, BILIBILI_SERVICE_ID)

        // Sentinel nextPage for the home feed's "Load More": not a real Page (see continueDiscoveryFeed()).
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

        @JvmStatic
        @Throws(ExtractionException::class)
        fun getDefaultSearchExtractor(service: StreamingService, query: String): SearchExtractor =
            service.getSearchExtractor(query)

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

        @JvmStatic
        fun isOriginalAudioTrack(stream: AudioStream): Boolean {
            return stream.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
        }

        // Default audio-track priority: original, then device locale, then English, then highest bitrate. Shared by the stream proxy, the manifest and the track picker.
        @JvmStatic
        fun audioTrackPriorityComparator(): Comparator<AudioStream> {
            val langCode = Locale.getDefault().language
            return Comparator { a, b ->
                val isOrigA = isOriginalAudioTrack(a)
                val isOrigB = isOriginalAudioTrack(b)
                if (isOrigA != isOrigB) {
                    return@Comparator if (isOrigA) -1 else 1
                }
                val localeA = a.audioLocale?.language
                val localeB = b.audioLocale?.language
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
            }
            return 0
        }

        // The manifest's video AdaptationSet only contains video-only MPEG_4 streams with valid SegmentBase ranges; the quality list must offer exactly that set, or a selection matches nothing.
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

        // One extractor and page fetch per video, whichever handler runs first.
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
        }
        val server = wsServer
        if (server != null) {
            try {
                server.stop()
            } catch (e: InterruptedException) {
            }
            wsServer = null
        }
        threadPool.shutdownNow()
        log("Local server stopped.")
    }

    // internal: handler groups are extension functions in sibling files.
    internal class ClientHandler(
        private val socket: Socket,
        internal val dbHelper: HistoryDbHelper,
        private val context: android.content.Context,
        private val executorService: ExecutorService
    ) : Runnable {

        // Set once at the top of run() so sendResponse() can check Accept-Encoding.
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

                        var path = rawUri
                        var query: String? = null
                        val qIdx = rawUri.indexOf("?")
                        if (qIdx >= 0) {
                            path = rawUri.substring(0, qIdx)
                            query = rawUri.substring(qIdx + 1)
                        }

                        val params = parseQueryParams(query)
                        log("Request: $method $path" + (if (query != null) "?$query" else ""))

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

                        val ua = requestHeaders["user-agent"]
                        var isTv = false
                        if (ua != null) {
                            val uaLower = ua.lowercase(Locale.US)
                            isTv = uaLower.contains("tv") || uaLower.contains("googletv") || uaLower.contains("androidtv") || uaLower.contains("smarttv") || uaLower.contains("appletv") || uaLower.contains("roku") || uaLower.contains("aftb") || uaLower.contains("aftt") || uaLower.contains("firetv")
                        }

                        try {
                            // Route table rebuilt per request: each lambda captures this request's state.
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
                                "/remote-state" to { handleRemoteState(os, params) },
                                "/release-lock" to { handleReleaseLock(os, params) },
                                "/history" to { handleHistory(os, params, isTv) },
                                "/history_action" to { handleHistoryAction(os, params) },
                                "/channel" to { handleChannel(os, params, isTv) },
                                "/playlist" to { handlePlaylist(os, params, isTv) },
                                "/stream" to { handleStreamProxy(os, params, requestHeaders) },
                                "/manifest" to { handleManifestProxy(os, params) },
                                "/subtitles" to { handleSubtitlesProxy(os, params) },
                                "/thumbnails" to { handleThumbnailsProxy(os, params) },
                                "/thumbnail-sheet" to { handleThumbnailSheetProxy(os, params) },
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
                                "/api/v1/history" to { handleApiHistory(os) },
                                "/api/v1/library" to { handleApiLibrary(os, params) },
                                "/api/v1/feed" to { handleApiFeed(os, params) },
                                "/api/v1/playlist" to { handleApiPlaylist(os, params) },
                                "/api/v1/state" to { handleApiState(os, params) },
                                "/api/v1/sponsor" to { handleApiSponsor(os, params) },
                                "/api/v1/settings" to { handleApiSettings(os, params) },
                                "/app" to { handleAppShell(os) },
                                "/app.css" to { handleAppCss(os) },
                                "/app.js" to { handleAppJs(os) },
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
                var items: List<InfoItem>
                // Ready-to-embed "Load More" value: a serialized Page, or HOME_DISCOVERY_LOAD_MORE_TOKEN for the personalized feed.
                var nextToken: String?

                val feedMode = dbHelper.homeFeedMode
                if (serviceId != SERVICE_YOUTUBE) {
                    // homeFeedMode ("subs"/"mix") is YouTube-only; other services get their kiosk feed.
                    val nextPage = HtmlRenderer.deserializePage(nextPageStr)
                    val page = LocalServerSource.home(dbHelper.appContext, serviceId, nextPage)
                    items = page.items
                    nextToken = HtmlRendererCommon.serializePage(page.next)
                } else if ("subs" == feedMode) {
                    items = dbHelper.buildSubsOnlyFeed(serviceId)
                    nextToken = null
                } else if (nextPageStr == HOME_DISCOVERY_LOAD_MORE_TOKEN) {
                    val (moreItems, hasMore) = dbHelper.continueDiscoveryFeed(serviceId)
                    items = moreItems
                    nextToken = if (hasMore) HOME_DISCOVERY_LOAD_MORE_TOKEN else null
                } else {
                    val (feedItems, hasMore) = dbHelper.buildAndRankHomeFeed(serviceId, feedMode)
                    items = feedItems
                    nextToken = if (hasMore) HOME_DISCOVERY_LOAD_MORE_TOKEN else null
                }

                val filtered = filterItems(items)
                val html = HtmlRenderer.renderHomeFeed(serviceId, filtered, nextToken)
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
            } catch (e: Exception) {
                // Any feed failure, not just connectivity: report the actual exception.
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

        // Merges subscribed channels' uploads into one newest-first feed (capped at 60). Each channel is resolved by its own URL, since subscriptions mix services.
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
                futures.add(executorService.submit(Callable {
                    val uploads =
                        if (BilibiliLink.isBilibili(url)) {
                            LocalServerBilibili.uploads(dbHelper.appContext, url, channel.name.orEmpty(), subscribedAvatarUrl.orEmpty())
                        } else {
                            fetchChannelUploads(NewPipe.getServiceByUrl(url), url)
                        }
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
            for ((index, future) in futures.withIndex()) {
                try {
                    // Bilibili's first request of a session sets up cookies and a signing key, which takes longer.
                    val waitSeconds = if (BilibiliLink.isBilibili(channels[index].url)) 20L else 5L
                    val res = future.get(waitSeconds, TimeUnit.SECONDS)
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

        @Throws(Exception::class)
        private fun handleSearch(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val query = params["q"]
            if (query.isNullOrEmpty()) {
                sendRedirect(os, "/?serviceId=$serviceId")
                return
            }
            // Only the initial page load adds a search-history entry; ajax follow-ups continue the same search.
            val isAjax = params["ajax"] == "1"
            if (!isAjax) {
                dbHelper.nativeAddSearchQuery(query)
            }

            val nextPageStr = params["nextPage"]
            val nextPage = HtmlRenderer.deserializePage(nextPageStr)

            try {
                val page = LocalServerSource.search(dbHelper.appContext, serviceId, query, nextPage)
                val items = page.items
                val next = page.next

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



        @Throws(Exception::class)
        private fun handleHistory(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val items = dbHelper.nativeHistory()
            val html = HtmlRenderer.renderHistory(serviceId, items, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }


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

            val hideWatched = dbHelper.nativeHideWatched()
            val hideShorts = dbHelper.nativeHideShorts()
            val homeFeedMode = dbHelper.homeFeedMode
            val saved = "true" == params["saved"]

            val html = HtmlRenderer.renderSettings(getServiceId(params), hideWatched, hideShorts, homeFeedMode, saved, isTv)
            sendResponse(os, 200, html, "text/html; charset=UTF-8")
        }

        private fun handleChannel(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val channelUrl = params["id"]!!
            val tab = params.getOrDefault("tab", "videos")
            // ajax=1 follow-ups only need the next grid batch, not the header chrome.
            val isAjax = params["ajax"] == "1"

            val nextPageStr = params["nextPage"]
            val nextPage = HtmlRenderer.deserializePage(nextPageStr)

            try {
                val channelTab = if ("playlists" == tab) "playlists" else "videos"
                val channel = LocalServerSource.channel(dbHelper.appContext, serviceId, channelUrl, channelTab, null, nextPage)
                val next = channel.next
                val filtered = filterItems(channel.items)
                val header = channel.header

                if (isAjax) {
                    val html = HtmlRenderer.renderChannelItemsFragment(serviceId, channelUrl, tab, filtered, next, header.avatarUrl)
                    sendResponse(os, 200, html, "text/html; charset=UTF-8")
                    return
                }

                val isSubscribed = dbHelper.nativeIsSubscribed(header.url)
                if (isSubscribed) {
                    header.avatarUrl?.let { dbHelper.nativeAddSubscription(header.url, header.name, HtmlRenderer.getThumbnailUrl(it)) }
                }
                val isBlocked = dbHelper.nativeIsChannelBlocked(header.url)
                val html = HtmlRenderer.renderChannel(serviceId, header, tab, filtered, next, isSubscribed, isBlocked, isTv)
                sendResponse(os, 200, html, "text/html; charset=UTF-8")
            } catch (e: Exception) {
                sendResponse(os, 500, "Channel failed: ${e.message}", "text/plain; charset=UTF-8")
            }
        }

        private fun handlePlaylist(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
            val serviceId = getServiceId(params)
            val playlistUrl = params["id"]!!
            // ajax=1 follow-ups only need the next grid batch, not the header chrome.
            val isAjax = params["ajax"] == "1"

            val nextPageStr = params["nextPage"]
            val nextPage = HtmlRenderer.deserializePage(nextPageStr)

            try {
                val playlist = LocalServerSource.playlist(dbHelper.appContext, serviceId, playlistUrl, nextPage)
                val next = playlist.next
                val filtered = filterItems(playlist.items)

                if (isAjax) {
                    val html = HtmlRenderer.renderPlaylistItemsFragment(serviceId, playlistUrl, filtered, next)
                    sendResponse(os, 200, html, "text/html; charset=UTF-8")
                    return
                }

                val isBookmarked = dbHelper.nativeIsPlaylistBookmarked(playlistUrl)
                val html = HtmlRenderer.renderPlaylist(serviceId, playlist.header, filtered, next, isBookmarked, isTv)
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
                }
            }
            return params
        }

        @Throws(IOException::class)
        internal fun sendResponse(os: OutputStream, code: Int, content: String, contentType: String) {
            sendResponse(os, code, content, contentType, null)
        }

        // cacheControl: header value, or null to omit; only the static handlers pass one.
        @Throws(IOException::class)
        internal fun sendResponse(os: OutputStream, code: Int, content: String, contentType: String, cacheControl: String?) {
            var bytes = content.toByteArray(Charsets.UTF_8)
            val status = if (code == 200) "OK" else (if (code == 404) "Not Found" else "Internal Server Error")

            // gzip for HTML and /static files; not for media (already compressed) or bodies under 512 bytes.
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

        // FlowNeuro signals: reported from handleWatchContent/handleApiVideo and the progress endpoint. Best-effort: failures never affect playback or the DB write.
        internal fun reportFlowNeuroClick(info: StreamInfo, serviceId: Int) {
            dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.CLICK)
        }

        // Ranking delegates to the native FlowNeuroEngine (rankWithFlowNeuro()); used by the home mix branch and handleApiRecommendations(), not handleApiHome().
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
