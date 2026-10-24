package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.recommendation.InteractionType
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import java.io.OutputStream

// `/api/v1/...` JSON handlers; extractor calls (fetchInitialOrPage, fetchKioskPage, ...) in LocalServerMedia.

internal fun RequestContext.handleApiSearch(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val query = params.required("q")
    val nextPage = WebFormat.deserializePage(params["nextPage"])
    if (nextPage == null) dbHelper.addSearchQuery(query)
    val page = LocalServerSource.search(dbHelper.appContext, serviceId, query, nextPage)
    reply.sendJson(ApiRenderer.searchResultJson(dbHelper.withoutHidden(page.items), serviceId, page.next))
}

internal fun RequestContext.handleApiHome(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val nextPage = WebFormat.deserializePage(params["nextPage"])
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
    reply.sendJson(ApiRenderer.searchResultJson(dbHelper.withoutHidden(items), serviceId, next))
}

// Ranked home feed: buildAndRankHomeFeed() on YouTube (FlowNeuroEngine); handleApiHome() stays raw.
// `more=1`: another discovery round (continueDiscoveryFeed()), not a rebuild of the ranked mix.
// No NewPipeExtractor Page behind it: reported as boolean "hasMore", not nextPage.
internal fun RequestContext.handleApiRecommendations(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val nextPage = WebFormat.deserializePage(params["nextPage"])
    val loadMore = params["more"] == "1"
    var items: List<InfoItem>
    var alreadyRanked = false
    var hasMore = false
    if (serviceId != LocalServerMedia.SERVICE_YOUTUBE) {
        // alreadyRanked false: dbHelper.rankWithFlowNeuro() below reorders. Service's own nextPage covers "more".
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
    val filtered = dbHelper.withoutHidden(items)
    val ranked = if (alreadyRanked) filtered else dbHelper.rankWithFlowNeuro(filtered, serviceId)
    reply.sendJson(ApiRenderer.searchResultJson(ranked, serviceId, null).put("hasMore", hasMore))
}

internal fun RequestContext.handleApiChannel(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val channelUrl = params.required("id")
    val tab = params.getOrDefault("tab", "videos")
    // Matches YoutubeChannelTabLinkHandlerFactory SORT_LATEST/POPULAR/OLDEST literals. YouTube "videos" tab only; no-op elsewhere.
    val sort = params["sort"]?.takeIf { it.isNotBlank() }
    val nextPage = WebFormat.deserializePage(params["nextPage"])
    val channel = LocalServerSource.channel(dbHelper.appContext, serviceId, channelUrl, tab, sort, nextPage)
    val isSubscribed = dbHelper.isSubscribed(channel.header.url)
    val json = JSONObject()
    json.put("channel", ApiRenderer.channelJson(channel.header, isSubscribed))
    json.put("videos", ApiRenderer.infoItemsToJson(dbHelper.withoutHidden(channel.items), serviceId))
    json.put("nextPage", ApiRenderer.serializePageOrNull(channel.next))
    reply.sendJson(json)
}

internal fun RequestContext.handleApiVideo(
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
    val uploaderAvatarUrl = info.uploaderAvatars?.takeIf { it.isNotEmpty() }?.let { WebFormat.getThumbnailUrl(it) }
    dbHelper.saveToHistory(info.name, info.url, info.uploaderName, thumbUrl, serviceId, info.uploaderUrl, uploaderAvatarUrl)
    // FlowNeuro click, best-effort.
    dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.CLICK)
    reply.sendJson(ApiRenderer.videoDetailJson(info, serviceId))
}

internal fun RequestContext.handleApiComments(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val videoUrl = params.required("id")
    val nextPage = WebFormat.deserializePage(params["nextPage"])
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

internal fun RequestContext.handleApiWatchProgress(
    reply: Reply,
    params: Map<String, String>,
) {
    val videoUrl = params.required("id")
    val percent = params["percent"]?.toIntOrNull() ?: throw BadRequestException("Missing or invalid 'percent' parameter")
    val durationSeconds = params["durationSeconds"]?.toIntOrNull() ?: 0
    dbHelper.updateWatchProgress(videoUrl, percent, durationSeconds)

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

internal fun RequestContext.handleApiHistory(reply: Reply) {
    reply.sendJson(JSONObject().put("videos", dbHelper.historyJson()))
}

// Subscriptions + bookmarked playlists + watch-later (Library screen).
internal fun RequestContext.handleApiLibrary(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val json = JSONObject()
    json.put("channels", ApiRenderer.infoItemsToJson(dbHelper.subscriptions(), serviceId))
    json.put("playlists", ApiRenderer.infoItemsToJson(dbHelper.bookmarkedPlaylists(), serviceId))
    json.put("watchLater", ApiRenderer.infoItemsToJson(dbHelper.watchLaterItems(), serviceId))
    json.put("mine", myPlaylistsJson(dbHelper.userPlaylists()))
    reply.sendJson(json)
}

// Subscription feed: newest uploads, one request per channel.
internal fun RequestContext.handleApiFeed(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val items = dbHelper.withoutHidden(subscriptionFeed(dbHelper.subscriptions()))
    reply.sendJson(ApiRenderer.searchResultJson(items, serviceId, null))
}

internal fun RequestContext.handleApiPlaylist(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val playlistUrl = params.required("id")
    val nextPage = WebFormat.deserializePage(params["nextPage"])
    val playlist = LocalServerSource.playlist(dbHelper.appContext, serviceId, playlistUrl, nextPage)
    val header = JSONObject()
    header.put("id", playlist.header.url)
    header.put("name", playlist.header.name)
    header.put("uploaderName", playlist.header.uploaderName)
    // -1 when source omits it; page omits the count
    header.put("videoCount", playlist.header.streamCount.coerceAtLeast(-1))
    header.put("isBookmarked", dbHelper.isPlaylistBookmarked(playlistUrl))
    val json = JSONObject()
    json.put("playlist", header)
    json.put("videos", ApiRenderer.infoItemsToJson(dbHelper.withoutHidden(playlist.items), serviceId))
    json.put("nextPage", ApiRenderer.serializePageOrNull(playlist.next))
    reply.sendJson(json)
}

// Watch-screen button state: channel subscribed/blocked, video saved/liked/disliked.
internal fun RequestContext.handleApiState(
    reply: Reply,
    params: Map<String, String>,
) {
    val videoUrl = params["id"].orEmpty()
    val channelUrl = params["channel"].orEmpty()
    val json = JSONObject()
    json.put("subscribed", channelUrl.isNotEmpty() && dbHelper.isSubscribed(channelUrl))
    json.put("blocked", channelUrl.isNotEmpty() && dbHelper.isChannelBlocked(channelUrl))
    json.put("watchLater", videoUrl.isNotEmpty() && dbHelper.isWatchLater(videoUrl))
    val like = if (videoUrl.isEmpty()) null else dbHelper.likeState(videoUrl)
    json.put("like", like ?: JSONObject.NULL)
    reply.sendJson(json)
}

// SponsorBlock segments (seconds): skip button + seek-bar marks; YouTube only.
internal fun RequestContext.handleApiSponsor(
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
internal fun RequestContext.handleApiSettings(
    reply: Reply,
    params: Map<String, String>,
) {
    val hideWatched = params["hideWatched"]
    if (hideWatched != null) dbHelper.setHideWatched(hideWatched == "true")
    val hideShorts = params["hideShorts"]
    if (hideShorts != null) dbHelper.setHideShorts(hideShorts == "true")
    val deepFlow = params["deepFlow"]
    if (deepFlow != null) dbHelper.setDeepFlow(deepFlow == "true")
    val json = JSONObject()
    json.put("hideWatched", dbHelper.hideWatched())
    json.put("hideShorts", dbHelper.hideShorts())
    val deepFlowStatus = dbHelper.deepFlow()
    json.put("deepFlow", deepFlowStatus.active)
    json.put("deepFlowEndsAt", deepFlowStatus.endsAt)
    reply.sendJson(json)
}
