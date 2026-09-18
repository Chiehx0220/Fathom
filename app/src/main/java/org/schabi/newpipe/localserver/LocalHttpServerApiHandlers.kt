package org.schabi.newpipe.localserver

import io.github.aedev.flow.data.recommendation.InteractionType
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.search.filter.Filter
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.localserver.LocalHttpServer.ClientHandler
import java.io.OutputStream

// The Fathom<->Flow Stage 2 JSON API (/api/v1/...) handlers, split out of LocalHttpServer.kt as
// extension functions on ClientHandler purely to keep that file from growing without bound - no
// behavior changed by the move. Deliberately independent of the HTML handlers (own extraction
// calls, not shared helpers) - see ApiRenderer.kt's file header for why. Calls into
// LocalHttpServer's companion object (fetchInitialOrPage, fetchKioskPage, etc.) are qualified
// because they're outside that class's own lexical scope here, not because they're private -
// none of them are.

internal fun ClientHandler.handleApiSearch(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val query = params["q"]
    if (query.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'q' parameter"), "application/json")
        return
    }
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        val service = NewPipe.getService(serviceId)
        val extractor = LocalHttpServer.getDefaultSearchExtractor(service, query)
        val page = LocalHttpServer.fetchInitialOrPage(extractor, nextPage)
        val filtered = filterItems(page.items)
        sendResponse(os, 200, ApiRenderer.searchResultJson(filtered, serviceId, page.nextPage).toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// Simplified relative to handleHome(): honors homeFeedMode ("subs" -> shuffled uploads from
// subscribed channels, "mix" -> personalized feed interleaved with subscriptions, default ->
// personalized-keyword-or-trending search) the same way, but skips the offline cached-video
// fallback handleHome() renders on failure - a JSON client is expected to handle a 500 itself
// rather than receive a page-shaped fallback.
internal fun ClientHandler.handleApiHome(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        val service = NewPipe.getService(serviceId)
        var items: List<InfoItem>
        var next: Page?
        val feedMode = dbHelper.homeFeedMode
        if (serviceId != LocalHttpServer.SERVICE_YOUTUBE) {
            // Non-YouTube default kiosk - see the same branch in handleHome() for why
            // homeFeedMode isn't consulted here.
            val page = LocalHttpServer.fetchKioskPage(service, nextPage)
            items = ArrayList(page.items as List<InfoItem>)
            next = page.nextPage
        } else if ("subs" == feedMode) {
            // See LocalServerFlowData.kt's buildSubsOnlyFeed().
            items = dbHelper.buildSubsOnlyFeed(serviceId)
            next = null
        } else {
            // Real trending kiosk - see fetchTrendingItems() (no pagination available, so
            // nextPage isn't handled for this branch).
            items = dbHelper.fetchTrendingItems(serviceId)
            next = null
        }
        val filtered = filterItems(items)
        sendResponse(os, 200, ApiRenderer.searchResultJson(filtered, serviceId, next).toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// New, additive route mirroring handleApiHome() above (same feedMode handling, same response
// shape via ApiRenderer.searchResultJson()) but sourced through buildAndRankHomeFeed() for the
// YouTube personalized path, so it comes back already ranked via Flow's real FlowNeuroEngine.
// handleApiHome() itself is untouched otherwise - this is a separate handler precisely so nothing
// about its raw/unranked behavior changes.
internal fun ClientHandler.handleApiRecommendations(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        val service = NewPipe.getService(serviceId)
        var items: List<InfoItem>
        var next: Page?
        var alreadyRanked = false
        val feedMode = dbHelper.homeFeedMode
        if (serviceId != LocalHttpServer.SERVICE_YOUTUBE) {
            // Non-YouTube default kiosk - see the same branch in handleHome() for why
            // homeFeedMode isn't consulted here. alreadyRanked stays false so
            // applyFlowNeuroRanking() below still reorders these by the user's taste.
            val page = LocalHttpServer.fetchKioskPage(service, nextPage)
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

internal fun ClientHandler.handleApiChannel(os: OutputStream, params: Map<String, String>) {
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

        // getChannelTabExtractorFromId(id, tab, baseUrl) (used by the plain else-branch below)
        // hardcodes its sortFilter to "" - it has no way to pass one through - so a non-default
        // sort needs the lower-level construction path built here directly instead. Stock
        // NewPipeExtractor's channel-tab factory has no "search within a channel" tab at all,
        // unlike the fork this was ported from, so that feature is dropped rather than adapted.
        val tabExtractor = if (sort != null && service.channelTabLHFactory != null) {
            val contentFilter = listOf(FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, tab))
            val sortFilter = listOf(FilterItem(Filter.ITEM_IDENTIFIER_UNKNOWN, sort))
            val linkHandler = service.channelTabLHFactory.fromQuery(
                channelExtractor.id, contentFilter, sortFilter, channelExtractor.baseUrl)
            service.getChannelTabExtractor(linkHandler)
        } else {
            LocalHttpServer.resolveChannelTabExtractor(service, channelExtractor, tab)
        }
        val page = LocalHttpServer.fetchInitialOrPage(tabExtractor, nextPage)
        val items = page.items
        val next = page.nextPage
        LocalHttpServer.backfillUploaderUrl(items, channelUrl)
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

internal fun ClientHandler.handleApiVideo(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]
    if (mediaUrl.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
        return
    }
    try {
        val service = NewPipe.getService(serviceId)
        val extractor = LocalHttpServer.getCachedExtractor(service, serviceId, mediaUrl)
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

internal fun ClientHandler.handleApiComments(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val videoUrl = params["id"]
    if (videoUrl.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
        return
    }
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        val service = NewPipe.getService(serviceId)
        val extractor = LocalHttpServer.commentsExtractorFor(service, videoUrl)
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

// Stage 4: lets a client (the Flow fork's new server-address settings screen) confirm it can
// actually reach this server before anything depends on it, without the cost of a real extraction
// call - every other /api/v1/... route does real work (search, extractor fetches) that isn't a
// fair test of plain reachability.
internal fun ClientHandler.handleApiPing(os: OutputStream) {
    sendResponse(os, 200, "{\"status\":\"ok\",\"service\":\"fathom\"}", "application/json")
}

internal fun ClientHandler.handleApiWatchProgress(os: OutputStream, params: Map<String, String>) {
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

    // FlowNeuro's WATCHED signal - see the "FlowNeuro signal reporting" section in
    // LocalHttpServer.kt for why this is best-effort. serviceId wasn't previously sent by this
    // endpoint's caller (watchProgressJs in HtmlRendererWatch.kt, updated alongside this); older
    // cached pages still open in a tab won't send it, so this is a no-op (not an error) until
    // reloaded.
    val serviceId = params["serviceId"]?.toIntOrNull()
    if (serviceId != null) {
        try {
            val service = NewPipe.getService(serviceId)
            val extractor = LocalHttpServer.getCachedExtractor(service, serviceId, videoUrl)
            val info: StreamInfo
            synchronized(extractor) {
                info = StreamInfo.getInfo(extractor)
            }
            dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.WATCHED, percent / 100f)
        } catch (e: Exception) {
            LocalHttpServer.log("FlowNeuro watch-signal error: " + e.message)
        }
    }

    sendResponse(os, 200, "{\"status\":\"ok\"}", "application/json")
}

// Remote native-audio-player control (play/pause/resume/stop) was dropped along with
// ServerService's own ExoPlayer instance - it duplicated the host app's own player. These four
// endpoints are kept as graceful no-ops so older clients hitting them don't see a broken request,
// rather than removing the routes outright.
internal fun ClientHandler.handleApiPlayerPlay(os: OutputStream, params: Map<String, String>) {
    sendResponse(os, 200, "{\"status\":\"unsupported\"}", "application/json")
}

internal fun ClientHandler.handleApiPlayerPause(os: OutputStream) {
    sendResponse(os, 200, "{\"status\":\"unsupported\"}", "application/json")
}

internal fun ClientHandler.handleApiPlayerResume(os: OutputStream) {
    sendResponse(os, 200, "{\"status\":\"unsupported\"}", "application/json")
}

internal fun ClientHandler.handleApiPlayerStop(os: OutputStream) {
    sendResponse(os, 200, "{\"status\":\"unsupported\"}", "application/json")
}
