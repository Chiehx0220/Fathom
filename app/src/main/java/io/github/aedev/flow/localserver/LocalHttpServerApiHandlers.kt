package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.recommendation.InteractionType
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import java.io.OutputStream

// `/api/v1/...` JSON handlers; extractor calls (fetchInitialOrPage, fetchKioskPage, ...) in LocalServerMedia.

internal fun ClientHandler.handleApiSearch(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val query = params.required("q")
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    if (nextPage == null) dbHelper.nativeAddSearchQuery(query)
    val page = LocalServerSource.search(dbHelper.appContext, serviceId, query, nextPage)
    reply.sendJson(ApiRenderer.searchResultJson(filterItems(page.items), serviceId, page.next))
}

internal fun ClientHandler.handleApiHome(
    reply: Reply,
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
    reply.sendJson(ApiRenderer.searchResultJson(filterItems(items), serviceId, next))
}

// Ranked home feed: buildAndRankHomeFeed() on YouTube (FlowNeuroEngine); handleApiHome() stays raw.
// `more=1`: another discovery round (continueDiscoveryFeed()), not a rebuild of the ranked mix.
// No NewPipeExtractor Page behind it: reported as boolean "hasMore", not nextPage.
internal fun ClientHandler.handleApiRecommendations(
    reply: Reply,
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
    reply.sendJson(ApiRenderer.searchResultJson(ranked, serviceId, null).put("hasMore", hasMore))
}

internal fun ClientHandler.handleApiChannel(
    reply: Reply,
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
    reply.sendJson(json)
}

internal fun ClientHandler.handleApiVideo(
    reply: Reply,
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
    reply.sendJson(ApiRenderer.videoDetailJson(info, serviceId))
}

internal fun ClientHandler.handleApiComments(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val videoUrl = params.required("id")
    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])
    val page = LocalServerSource.comments(dbHelper.appContext, serviceId, videoUrl, nextPage)
    if (page.disabled) {
        reply.sendJson("{\"comments\":[],\"nextPage\":null,\"commentsDisabled\":true}")
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
    reply.sendJson(json)
}

internal fun ClientHandler.handleApiWatchProgress(
    reply: Reply,
    params: Map<String, String>,
) {
    val videoUrl = params.required("id")
    val percent = params["percent"]?.toIntOrNull() ?: throw BadRequestException("Missing or invalid 'percent' parameter")
    val durationSeconds = params["durationSeconds"]?.toIntOrNull() ?: 0
    dbHelper.nativeUpdateWatchProgress(videoUrl, percent, durationSeconds)

    // FlowNeuro WATCHED signal, best-effort; no serviceId → no-op.
    val serviceId = params["serviceId"]?.toIntOrNull()
    if (serviceId != null) {
        try {
            val info = LocalServerSource.streamInfo(dbHelper.appContext, serviceId, videoUrl)
            dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.WATCHED, percent / 100f)
        } catch (e: Exception) {
            serverLog("FlowNeuro watch-signal error: " + e.message)
        }
    }

    reply.sendJson("{\"status\":\"ok\"}")
}

// Web app endpoints (JSON).

internal fun ClientHandler.handleApiHistory(reply: Reply) {
    reply.sendJson(JSONObject().put("videos", dbHelper.nativeHistoryJson()))
}

// Subscriptions + bookmarked playlists + watch-later (Library screen).
internal fun ClientHandler.handleApiLibrary(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val json = JSONObject()
    json.put("channels", ApiRenderer.infoItemsToJson(dbHelper.nativeSubscriptions(), serviceId))
    json.put("playlists", ApiRenderer.infoItemsToJson(dbHelper.nativeBookmarkedPlaylists(), serviceId))
    json.put("watchLater", ApiRenderer.infoItemsToJson(dbHelper.nativeWatchLaterItems(), serviceId))
    json.put("mine", myPlaylistsJson(dbHelper.nativeUserPlaylists()))
    reply.sendJson(json)
}

// Subscription feed: newest uploads, one request per channel.
internal fun ClientHandler.handleApiFeed(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val items = filterItems(fetchSubscriptionFeed(dbHelper.nativeSubscriptions()))
    reply.sendJson(ApiRenderer.searchResultJson(items, serviceId, null))
}

internal fun ClientHandler.handleApiPlaylist(
    reply: Reply,
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
    reply.sendJson(json)
}

// Watch-screen button state: channel subscribed/blocked, video saved/liked/disliked.
internal fun ClientHandler.handleApiState(
    reply: Reply,
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
    reply.sendJson(json)
}

// SponsorBlock segments (seconds): skip button + seek-bar marks; YouTube only.
internal fun ClientHandler.handleApiSponsor(
    reply: Reply,
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
    reply.sendJson(json)
}

// Filter settings; query params saved first.
internal fun ClientHandler.handleApiSettings(
    reply: Reply,
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
    reply.sendJson(json)
}
