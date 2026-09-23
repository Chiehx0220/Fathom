package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.recommendation.InteractionType
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import io.github.aedev.flow.localserver.LocalHttpServer.ClientHandler
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
    if (nextPage == null) dbHelper.nativeAddSearchQuery(query)
    try {
        val page = LocalServerSource.search(dbHelper.appContext, serviceId, query, nextPage)
        val filtered = filterItems(page.items)
        sendResponse(os, 200, ApiRenderer.searchResultJson(filtered, serviceId, page.next).toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

internal fun ClientHandler.handleApiHome(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        var items: List<InfoItem>
        var next: Page?
        val feedMode = dbHelper.homeFeedMode
        if (serviceId != LocalHttpServer.SERVICE_YOUTUBE) {
            // homeFeedMode is YouTube-only.
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
//
// `more=1` asks for another discovery round instead of rebuilding the ranked mix - the same
// "load more" native Home uses (continueDiscoveryFeed()), so the web app's home feed is not
// capped at whatever the first ranked batch happened to contain. There is no NewPipeExtractor
// Page behind that round (FlowNeuroEngine just goes one query-depth deeper), so it is reported
// to the client as a plain "hasMore" boolean rather than through the searchResultJson nextPage
// mechanism the Page-backed callers (search/channel/playlist) use.
internal fun ClientHandler.handleApiRecommendations(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    val loadMore = params["more"] == "1"
    try {
        var items: List<InfoItem>
        var alreadyRanked = false
        var hasMore = false
        val feedMode = dbHelper.homeFeedMode
        if (serviceId != LocalHttpServer.SERVICE_YOUTUBE) {
            // homeFeedMode is YouTube-only. alreadyRanked stays false so applyFlowNeuroRanking()
            // below still reorders these. This service's own paging (nextPage) covers "more" here.
            val page = LocalServerSource.home(dbHelper.appContext, serviceId, nextPage)
            items = page.items
            hasMore = page.next != null
        } else if ("subs" == feedMode) {
            // Uncapped "just my subscriptions" pool (see buildSubsOnlyFeed's own doc) - first-load
            // only, matching native: there is no deeper round to fetch for this mode.
            items = dbHelper.buildSubsOnlyFeed(serviceId)
            alreadyRanked = true
        } else if (loadMore) {
            val (feedItems, more) = dbHelper.continueDiscoveryFeed(serviceId)
            items = feedItems
            alreadyRanked = true
            hasMore = more
        } else {
            // buildAndRankHomeFeed(): trending + discovery + subs, ranked. Its own "more" flag
            // just means "non-empty" - continueDiscoveryFeed's own flag governs later rounds.
            val (feedItems, more) = dbHelper.buildAndRankHomeFeed(serviceId, feedMode)
            items = feedItems
            alreadyRanked = true
            hasMore = more
        }
        val filtered = filterItems(items)
        val ranked = if (alreadyRanked) filtered else applyFlowNeuroRanking(filtered, serviceId)
        val json = ApiRenderer.searchResultJson(ranked, serviceId, null)
        json.put("hasMore", hasMore)
        sendResponse(os, 200, json.toString(), "application/json")
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
    // is a newer param (assets/web/js/player.js's initWatchProgressReporting()) - a stale cached page
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

// The endpoints below feed the single-page web app (/app). They return what the HTML pages used to embed, as JSON.

internal fun ClientHandler.handleApiHistory(os: OutputStream) {
    try {
        val json = org.json.JSONObject()
        json.put("videos", dbHelper.nativeHistoryJson())
        sendResponse(os, 200, json.toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// Subscribed channels, bookmarked playlists and the watch-later list in one response (the Library screen shows all three).
internal fun ClientHandler.handleApiLibrary(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    try {
        val json = org.json.JSONObject()
        json.put("channels", ApiRenderer.infoItemsToJson(dbHelper.nativeSubscriptions(), serviceId))
        json.put("playlists", ApiRenderer.infoItemsToJson(dbHelper.nativeBookmarkedPlaylists(), serviceId))
        json.put("watchLater", ApiRenderer.infoItemsToJson(dbHelper.nativeWatchLaterItems(), serviceId))
        sendResponse(os, 200, json.toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// Newest uploads of the subscribed channels; slow (one request per channel), so the app asks for it separately.
internal fun ClientHandler.handleApiFeed(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    try {
        val items = filterItems(fetchSubscriptionFeed(dbHelper.nativeSubscriptions()))
        sendResponse(os, 200, ApiRenderer.searchResultJson(items, serviceId, null).toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

internal fun ClientHandler.handleApiPlaylist(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val playlistUrl = params["id"]
    if (playlistUrl.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
        return
    }
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        val playlist = LocalServerSource.playlist(dbHelper.appContext, serviceId, playlistUrl, nextPage)
        val json = org.json.JSONObject()
        val header = org.json.JSONObject()
        header.put("id", playlist.header.url)
        header.put("name", playlist.header.name)
        header.put("uploaderName", playlist.header.uploaderName)
        header.put("videoCount", playlist.header.streamCount.coerceAtLeast(0))
        header.put("isBookmarked", dbHelper.nativeIsPlaylistBookmarked(playlistUrl))
        json.put("playlist", header)
        json.put("videos", ApiRenderer.infoItemsToJson(filterItems(playlist.items), serviceId))
        json.put("nextPage", ApiRenderer.serializePageOrNull(playlist.next))
        sendResponse(os, 200, json.toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// What the watch screen's buttons need to show: is the channel subscribed or blocked, is the video saved, liked or disliked.
internal fun ClientHandler.handleApiState(os: OutputStream, params: Map<String, String>) {
    val videoUrl = params["id"].orEmpty()
    val channelUrl = params["channel"].orEmpty()
    val json = org.json.JSONObject()
    json.put("subscribed", channelUrl.isNotEmpty() && dbHelper.nativeIsSubscribed(channelUrl))
    json.put("blocked", channelUrl.isNotEmpty() && dbHelper.nativeIsChannelBlocked(channelUrl))
    json.put("watchLater", videoUrl.isNotEmpty() && dbHelper.nativeIsWatchLater(videoUrl))
    val like = if (videoUrl.isEmpty()) null else dbHelper.nativeLikeState(videoUrl)
    json.put("like", like ?: org.json.JSONObject.NULL)
    sendResponse(os, 200, json.toString(), "application/json")
}

// SponsorBlock segments (seconds) for the skip button and seek-bar marks; YouTube only.
internal fun ClientHandler.handleApiSponsor(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val videoUrl = params["id"].orEmpty()
    val array = org.json.JSONArray()
    if (serviceId == LocalHttpServer.SERVICE_YOUTUBE && videoUrl.isNotEmpty()) {
        try {
            for (segment in SponsorBlockClient.fetchSegments(LocalHttpServer.getVideoId(videoUrl))) {
                val label =
                    when (segment.category) {
                        "sponsor" -> "sponsor"
                        "intro" -> "intro"
                        "outro" -> "outro"
                        "interaction" -> "reminder"
                        "selfpromo" -> "self-promotion"
                        "music_offtopic" -> "non-music part"
                        else -> null
                    } ?: continue
                val item = org.json.JSONObject()
                item.put("s", segment.startMs / 1000.0)
                item.put("e", segment.endMs / 1000.0)
                item.put("c", segment.category)
                item.put("l", label)
                array.put(item)
            }
        } catch (e: Exception) {
            LocalHttpServer.log("SponsorBlock error: " + e.message)
        }
    }
    val json = org.json.JSONObject()
    json.put("segments", array)
    sendResponse(os, 200, json.toString(), "application/json")
}

// Reads the filter settings; any of hideWatched, hideShorts and homeFeedMode in the query are saved first.
internal fun ClientHandler.handleApiSettings(os: OutputStream, params: Map<String, String>) {
    val hideWatched = params["hideWatched"]
    if (hideWatched != null) dbHelper.nativeSetHideWatched(hideWatched == "true")
    val hideShorts = params["hideShorts"]
    if (hideShorts != null) dbHelper.nativeSetHideShorts(hideShorts == "true")
    val feedMode = params["homeFeedMode"]
    if (feedMode != null && (feedMode == "mix" || feedMode == "subs" || feedMode == "recs")) dbHelper.homeFeedMode = feedMode
    val json = org.json.JSONObject()
    json.put("hideWatched", dbHelper.nativeHideWatched())
    json.put("hideShorts", dbHelper.nativeHideShorts())
    json.put("homeFeedMode", dbHelper.homeFeedMode)
    sendResponse(os, 200, json.toString(), "application/json")
}
