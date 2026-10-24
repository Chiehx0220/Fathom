package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.recommendation.InteractionType
import org.json.JSONArray
import org.json.JSONObject
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
    val query = params.required("q")
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    if (nextPage == null) dbHelper.nativeAddSearchQuery(query)
    val page = LocalServerSource.search(dbHelper.appContext, serviceId, query, nextPage)
    sendJson(os, ApiRenderer.searchResultJson(filterItems(page.items), serviceId, page.next))
}

internal fun ClientHandler.handleApiHome(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    val items: List<InfoItem>
    val next: Page?
    if (serviceId != LocalServerMedia.SERVICE_YOUTUBE) {
        val page = LocalServerSource.home(dbHelper.appContext, serviceId, nextPage)
        items = page.items
        next = page.next
    } else {
        // fetchTrendingItems(): no pagination.
        items = dbHelper.fetchTrendingItems(serviceId)
        next = null
    }
    sendJson(os, ApiRenderer.searchResultJson(filterItems(items), serviceId, next))
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
    sendJson(os, ApiRenderer.searchResultJson(ranked, serviceId, null).put("hasMore", hasMore))
}

internal fun ClientHandler.handleApiChannel(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val channelUrl = params.required("id")
    val tab = params.getOrDefault("tab", "videos")
    // Matches YoutubeChannelTabLinkHandlerFactory SORT_LATEST/POPULAR/OLDEST literals. YouTube "videos" tab only; no-op elsewhere.
    val sort = params["sort"]?.takeIf { it.isNotBlank() }
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    val channel = LocalServerSource.channel(dbHelper.appContext, serviceId, channelUrl, tab, sort, nextPage)
    val isSubscribed = dbHelper.nativeIsSubscribed(channel.header.url)
    val json = JSONObject()
    json.put("channel", ApiRenderer.channelJson(channel.header, isSubscribed))
    json.put("videos", ApiRenderer.infoItemsToJson(filterItems(channel.items), serviceId))
    json.put("nextPage", ApiRenderer.serializePageOrNull(channel.next))
    sendJson(os, json)
}

internal fun ClientHandler.handleApiVideo(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val info = LocalServerSource.watchInfo(dbHelper, serviceId, params.required("id"))
    val thumbUrl =
        info.thumbnails
            ?.lastOrNull()
            ?.url
            .orEmpty()
    val uploaderAvatarUrl = info.uploaderAvatars?.takeIf { it.isNotEmpty() }?.let { HtmlRenderer.getThumbnailUrl(it) }
    dbHelper.nativeSaveToHistory(info.name, info.url, info.uploaderName, thumbUrl, serviceId, info.uploaderUrl, uploaderAvatarUrl)
    reportFlowNeuroClick(info, serviceId)
    sendJson(os, ApiRenderer.videoDetailJson(info, serviceId))
}

internal fun ClientHandler.handleApiComments(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val videoUrl = params.required("id")
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    val page = LocalServerSource.comments(dbHelper.appContext, serviceId, videoUrl, nextPage)
    if (page.disabled) {
        sendJson(os, "{\"comments\":[],\"nextPage\":null,\"commentsDisabled\":true}")
        return
    }
    val comments = JSONArray()
    for (item in page.items) {
        comments.put(ApiRenderer.commentJson(item))
    }
    val json = JSONObject()
    json.put("comments", comments)
    json.put("nextPage", ApiRenderer.serializePageOrNull(page.next))
    json.put("commentsDisabled", false)
    sendJson(os, json)
}

internal fun ClientHandler.handleApiWatchProgress(
    os: OutputStream,
    params: Map<String, String>,
) {
    val videoUrl = params.required("id")
    val percent = params["percent"]?.toIntOrNull() ?: throw BadRequestException("Missing or invalid 'percent' parameter")
    val durationSeconds = params["durationSeconds"]?.toIntOrNull() ?: 0
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

    sendJson(os, "{\"status\":\"ok\"}")
}

// Single-page web app (/app) endpoints: JSON of what HTML pages embedded.

internal fun ClientHandler.handleApiHistory(os: OutputStream) {
    sendJson(os, JSONObject().put("videos", dbHelper.nativeHistoryJson()))
}

// Subscriptions + bookmarked playlists + watch-later (Library screen).
internal fun ClientHandler.handleApiLibrary(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val json = JSONObject()
    json.put("channels", ApiRenderer.infoItemsToJson(dbHelper.nativeSubscriptions(), serviceId))
    json.put("playlists", ApiRenderer.infoItemsToJson(dbHelper.nativeBookmarkedPlaylists(), serviceId))
    json.put("watchLater", ApiRenderer.infoItemsToJson(dbHelper.nativeWatchLaterItems(), serviceId))
    json.put("mine", myPlaylistsJson(dbHelper.nativeUserPlaylists()))
    sendJson(os, json)
}

// Newest uploads of subscribed channels; one request per channel: separate call.
internal fun ClientHandler.handleApiFeed(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val items = filterItems(fetchSubscriptionFeed(dbHelper.nativeSubscriptions()))
    sendJson(os, ApiRenderer.searchResultJson(items, serviceId, null))
}

internal fun ClientHandler.handleApiPlaylist(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val playlistUrl = params.required("id")
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    val playlist = LocalServerSource.playlist(dbHelper.appContext, serviceId, playlistUrl, nextPage)
    val header = JSONObject()
    header.put("id", playlist.header.url)
    header.put("name", playlist.header.name)
    header.put("uploaderName", playlist.header.uploaderName)
    // -1 when source omits it; page omits the count
    header.put("videoCount", playlist.header.streamCount.coerceAtLeast(-1))
    header.put("isBookmarked", dbHelper.nativeIsPlaylistBookmarked(playlistUrl))
    val json = JSONObject()
    json.put("playlist", header)
    json.put("videos", ApiRenderer.infoItemsToJson(filterItems(playlist.items), serviceId))
    json.put("nextPage", ApiRenderer.serializePageOrNull(playlist.next))
    sendJson(os, json)
}

// Watch-screen button state: channel subscribed/blocked, video saved/liked/disliked.
internal fun ClientHandler.handleApiState(
    os: OutputStream,
    params: Map<String, String>,
) {
    val videoUrl = params["id"].orEmpty()
    val channelUrl = params["channel"].orEmpty()
    val json = JSONObject()
    json.put("subscribed", channelUrl.isNotEmpty() && dbHelper.nativeIsSubscribed(channelUrl))
    json.put("blocked", channelUrl.isNotEmpty() && dbHelper.nativeIsChannelBlocked(channelUrl))
    json.put("watchLater", videoUrl.isNotEmpty() && dbHelper.nativeIsWatchLater(videoUrl))
    val like = if (videoUrl.isEmpty()) null else dbHelper.nativeLikeState(videoUrl)
    json.put("like", like ?: JSONObject.NULL)
    sendJson(os, json)
}

// SponsorBlock segments (seconds): skip button + seek-bar marks; YouTube only.
internal fun ClientHandler.handleApiSponsor(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val videoUrl = params["id"].orEmpty()
    val array = JSONArray()
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
                val item = JSONObject()
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
    val json = JSONObject()
    json.put("segments", array)
    sendJson(os, json)
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
    val json = JSONObject()
    json.put("hideWatched", dbHelper.nativeHideWatched())
    json.put("hideShorts", dbHelper.nativeHideShorts())
    val deepFlowStatus = dbHelper.nativeDeepFlow()
    json.put("deepFlow", deepFlowStatus.active)
    json.put("deepFlowEndsAt", deepFlowStatus.endsAt)
    sendJson(os, json)
}
