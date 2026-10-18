package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.recommendation.InteractionType
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import java.io.OutputStream

// `/api/v1/...` JSON handlers, split from LocalHttpServer.kt, no behavior change. Independent of HTML handlers (ApiRenderer.kt).
// Companion calls (fetchInitialOrPage, fetchKioskPage, ...) in LocalServerMedia.

internal fun ClientHandler.handleApiSearch(
    os: OutputStream,
    params: Map<String, String>,
) {
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

internal fun ClientHandler.handleApiHome(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    try {
        var items: List<InfoItem>
        var next: Page?
        if (serviceId != LocalServerMedia.SERVICE_YOUTUBE) {
            val page = LocalServerSource.home(dbHelper.appContext, serviceId, nextPage)
            items = page.items
            next = page.next
        } else {
            // fetchTrendingItems(): no pagination.
            items = dbHelper.fetchTrendingItems(serviceId)
            next = null
        }
        val filtered = filterItems(items)
        sendResponse(os, 200, ApiRenderer.searchResultJson(filtered, serviceId, next).toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// Additive route mirroring handleApiHome(), via buildAndRankHomeFeed() on YouTube: FlowNeuroEngine-ranked; handleApiHome() stays raw.
//
// `more=1`: another discovery round (continueDiscoveryFeed(), as native Home load-more), not rebuild of ranked mix.
// No NewPipeExtractor Page behind it (FlowNeuroEngine goes one query-depth deeper): reported as boolean "hasMore",
// not via searchResultJson nextPage.
internal fun ClientHandler.handleApiRecommendations(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    val loadMore = params["more"] == "1"
    try {
        var items: List<InfoItem>
        var alreadyRanked = false
        var hasMore = false
        if (serviceId != LocalServerMedia.SERVICE_YOUTUBE) {
            // alreadyRanked false: applyFlowNeuroRanking() below reorders. Service's own nextPage covers "more".
            val page = LocalServerSource.home(dbHelper.appContext, serviceId, nextPage)
            items = page.items
            hasMore = page.next != null
        } else if (loadMore) {
            val (feedItems, more) = dbHelper.continueDiscoveryFeed(serviceId)
            items = feedItems
            alreadyRanked = true
            hasMore = more
        } else {
            // buildAndRankHomeFeed(): trending + discovery + subs, ranked. "more" = non-empty; later rounds governed by continueDiscoveryFeed.
            val (feedItems, more) = dbHelper.buildAndRankHomeFeed(serviceId)
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

internal fun ClientHandler.handleApiChannel(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val channelUrl = params["id"]
    if (channelUrl.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
        return
    }
    val tab = params.getOrDefault("tab", "videos")
    // Matches YoutubeChannelTabLinkHandlerFactory SORT_LATEST/POPULAR/OLDEST literals. YouTube "videos" tab only; no-op elsewhere.
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

internal fun ClientHandler.handleApiVideo(
    os: OutputStream,
    params: Map<String, String>,
) {
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

internal fun ClientHandler.handleApiComments(
    os: OutputStream,
    params: Map<String, String>,
) {
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

// Reachability check for server-address settings; no extraction cost.
internal fun ClientHandler.handleApiPing(os: OutputStream) {
    sendResponse(os, 200, "{\"status\":\"ok\",\"service\":\"fathom\"}", "application/json")
}

internal fun ClientHandler.handleApiWatchProgress(
    os: OutputStream,
    params: Map<String, String>,
) {
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

    // FlowNeuro WATCHED signal, best-effort (LocalHttpServer.kt FlowNeuro section). serviceId: newer param (player.js initWatchProgressReporting()); stale cached page without it: no-op.
    val serviceId = params["serviceId"]?.toIntOrNull()
    if (serviceId != null) {
        try {
            val info = LocalServerSource.streamInfo(dbHelper.appContext, serviceId, videoUrl)
            dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.WATCHED, percent / 100f)
        } catch (e: Exception) {
            serverLog("FlowNeuro watch-signal error: " + e.message)
        }
    }

    sendResponse(os, 200, "{\"status\":\"ok\"}", "application/json")
}

// Native audio-player control dropped with ServerService ExoPlayer (duplicated host player); no-op kept for older clients.
internal fun ClientHandler.handleApiPlayerPlay(
    os: OutputStream,
    params: Map<String, String>,
) {
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

// Single-page web app (/app) endpoints: JSON of what HTML pages embedded.

internal fun ClientHandler.handleApiHistory(os: OutputStream) {
    try {
        val json = org.json.JSONObject()
        json.put("videos", dbHelper.nativeHistoryJson())
        sendResponse(os, 200, json.toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// Subscriptions + bookmarked playlists + watch-later (Library screen).
internal fun ClientHandler.handleApiLibrary(
    os: OutputStream,
    params: Map<String, String>,
) {
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

// Newest uploads of subscribed channels; one request per channel: separate call.
internal fun ClientHandler.handleApiFeed(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    try {
        val items = filterItems(fetchSubscriptionFeed(dbHelper.nativeSubscriptions()))
        sendResponse(os, 200, ApiRenderer.searchResultJson(items, serviceId, null).toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

internal fun ClientHandler.handleApiPlaylist(
    os: OutputStream,
    params: Map<String, String>,
) {
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
        // -1 when source omits it; page omits the count
        header.put("videoCount", playlist.header.streamCount.coerceAtLeast(-1))
        header.put("isBookmarked", dbHelper.nativeIsPlaylistBookmarked(playlistUrl))
        json.put("playlist", header)
        json.put("videos", ApiRenderer.infoItemsToJson(filterItems(playlist.items), serviceId))
        json.put("nextPage", ApiRenderer.serializePageOrNull(playlist.next))
        sendResponse(os, 200, json.toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

// Watch-screen button state: channel subscribed/blocked, video saved/liked/disliked.
internal fun ClientHandler.handleApiState(
    os: OutputStream,
    params: Map<String, String>,
) {
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

// SponsorBlock segments (seconds): skip button + seek-bar marks; YouTube only.
internal fun ClientHandler.handleApiSponsor(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val videoUrl = params["id"].orEmpty()
    val array = org.json.JSONArray()
    if (serviceId == LocalServerMedia.SERVICE_YOUTUBE && videoUrl.isNotEmpty()) {
        try {
            for (segment in SponsorBlockClient.fetchSegments(LocalServerMedia.getVideoId(videoUrl))) {
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
            serverLog("SponsorBlock error: " + e.message)
        }
    }
    val json = org.json.JSONObject()
    json.put("segments", array)
    sendResponse(os, 200, json.toString(), "application/json")
}

// Reads filter settings; hideWatched, hideShorts in query saved first.
internal fun ClientHandler.handleApiSettings(
    os: OutputStream,
    params: Map<String, String>,
) {
    val hideWatched = params["hideWatched"]
    if (hideWatched != null) dbHelper.nativeSetHideWatched(hideWatched == "true")
    val hideShorts = params["hideShorts"]
    if (hideShorts != null) dbHelper.nativeSetHideShorts(hideShorts == "true")
    val deepFlow = params["deepFlow"]
    if (deepFlow != null) dbHelper.nativeSetDeepFlow(deepFlow == "true")
    val json = org.json.JSONObject()
    json.put("hideWatched", dbHelper.nativeHideWatched())
    json.put("hideShorts", dbHelper.nativeHideShorts())
    val deepFlowStatus = dbHelper.nativeDeepFlow()
    json.put("deepFlow", deepFlowStatus.active)
    json.put("deepFlowEndsAt", deepFlowStatus.endsAt)
    sendResponse(os, 200, json.toString(), "application/json")
}
