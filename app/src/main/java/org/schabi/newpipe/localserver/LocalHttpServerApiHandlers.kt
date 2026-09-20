package org.schabi.newpipe.localserver

import io.github.aedev.flow.data.recommendation.InteractionType
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.localserver.LocalHttpServer.ClientHandler
import java.io.OutputStream

// /api/v1/... JSON API handlers - split from LocalHttpServer.kt, no behavior change. Independent
// of the HTML handlers (see ApiRenderer.kt's header). Companion calls (fetchInitialOrPage,
// fetchKioskPage, etc.) need LocalHttpServer. qualification: outside lexical scope across files.

internal fun ClientHandler.handleApiSearch(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val query = params["q"]
    if (query.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'q' parameter"), "application/json")
        return
    }
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        val page = LocalServerSource.search(dbHelper.appContext, serviceId, query, nextPage)
        val filtered = filterItems(page.items)
        sendResponse(os, 200, ApiRenderer.searchResultJson(filtered, serviceId, page.next).toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// Mirrors handleHome()'s homeFeedMode handling, minus the offline cached-video fallback - a JSON
// client handles a 500 itself.
internal fun ClientHandler.handleApiHome(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        var items: List<InfoItem>
        var next: Page?
        val feedMode = dbHelper.homeFeedMode
        if (serviceId != LocalHttpServer.SERVICE_YOUTUBE) {
            // homeFeedMode is YouTube-only, see handleHome().
            val page = LocalServerSource.home(dbHelper.appContext, serviceId, nextPage)
            items = page.items
            next = page.next
        } else if ("subs" == feedMode) {
            items = dbHelper.buildSubsOnlyFeed(serviceId)
            next = null
        } else {
            // fetchTrendingItems(): no pagination available.
            items = dbHelper.fetchTrendingItems(serviceId)
            next = null
        }
        val filtered = filterItems(items)
        sendResponse(os, 200, ApiRenderer.searchResultJson(filtered, serviceId, next).toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// Additive route mirroring handleApiHome(), sourced through buildAndRankHomeFeed() for the
// YouTube path - ranked via FlowNeuroEngine. handleApiHome() itself stays raw/unranked.
internal fun ClientHandler.handleApiRecommendations(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        var items: List<InfoItem>
        var next: Page?
        var alreadyRanked = false
        val feedMode = dbHelper.homeFeedMode
        if (serviceId != LocalHttpServer.SERVICE_YOUTUBE) {
            // homeFeedMode is YouTube-only. alreadyRanked stays false so applyFlowNeuroRanking()
            // below still reorders these.
            val page = LocalServerSource.home(dbHelper.appContext, serviceId, nextPage)
            items = page.items
            next = page.next
        } else if ("subs" == feedMode) {
            items = dbHelper.buildSubsOnlyFeed(serviceId)
            next = null
            alreadyRanked = true
        } else {
            // buildAndRankHomeFeed(): trending + discovery + subs, ranked. No pagination.
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
    // Matches YoutubeChannelTabLinkHandlerFactory's SORT_LATEST/POPULAR/OLDEST literals directly,
    // no lookup table needed. YouTube "videos" tab only; no-op elsewhere.
    val sort = params["sort"]?.takeIf { it.isNotBlank() }
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        val channel = LocalServerSource.channel(dbHelper.appContext, serviceId, channelUrl, tab, sort, nextPage)
        val isSubscribed = dbHelper.nativeIsSubscribed(channel.header.url)
        val filtered = filterItems(channel.items)

        val json = org.json.JSONObject()
        json.put("channel", ApiRenderer.channelJson(channel.header, isSubscribed))
        json.put("videos", ApiRenderer.infoItemsToJson(filtered, serviceId))
        json.put("nextPage", ApiRenderer.serializePageOrNull(channel.next))
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
        val info = LocalServerSource.watchInfo(dbHelper, serviceId, mediaUrl)
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
        val page = LocalServerSource.comments(dbHelper.appContext, serviceId, videoUrl, nextPage)
        if (page.disabled) {
            sendResponse(os, 200, "{\"comments\":[],\"nextPage\":null,\"commentsDisabled\":true}", "application/json")
            return
        }
        val comments = org.json.JSONArray()
        for (item in page.items) {
            comments.put(ApiRenderer.commentJson(item))
        }
        val json = org.json.JSONObject()
        json.put("comments", comments)
        json.put("nextPage", ApiRenderer.serializePageOrNull(page.next))
        json.put("commentsDisabled", false)
        sendResponse(os, 200, json.toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// Reachability check for the server-address settings screen - no extraction cost, unlike every
// other /api/v1/... route.
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

    // FlowNeuro WATCHED signal, best-effort (see LocalHttpServer.kt's FlowNeuro section). serviceId
    // is a newer param (HtmlScripts.kt's initWatchProgressReporting()) - a stale cached page
    // without it is a silent no-op.
    val serviceId = params["serviceId"]?.toIntOrNull()
    if (serviceId != null) {
        try {
            val info = LocalServerSource.streamInfo(dbHelper.appContext, serviceId, videoUrl)
            dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.WATCHED, percent / 100f)
        } catch (e: Exception) {
            LocalHttpServer.log("FlowNeuro watch-signal error: " + e.message)
        }
    }

    sendResponse(os, 200, "{\"status\":\"ok\"}", "application/json")
}

// Native-audio-player control dropped with ServerService's ExoPlayer instance (duplicated the
// host app's player). Kept as no-ops for older clients, not removed outright.
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
