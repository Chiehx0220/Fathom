package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.recommendation.InteractionType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import java.io.OutputStream

// `/api/v1/...` JSON handlers; extractor calls (fetchInitialOrPage, fetchKioskPage, ...) in LocalServerMedia.

internal fun RequestContext.handleApiSearch(
    reply: Reply,
    params: Query,
) {
    val serviceId = getServiceId(params)
    val query = params.required("q")
    val nextPage = params.page("nextPage")
    if (nextPage == null) dbHelper.addSearchQuery(query)
    val page = LocalServerSource.search(dbHelper.appContext, serviceId, query, nextPage)
    reply.sendJson(dbHelper.withoutHidden(page.items).toSearchResult(page.next))
}

internal fun RequestContext.handleApiHome(
    reply: Reply,
    params: Query,
) {
    val serviceId = getServiceId(params)
    val nextPage = params.page("nextPage")
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
    reply.sendJson(dbHelper.withoutHidden(items).toSearchResult(next))
}

// Ranked home feed: buildAndRankHomeFeed() on YouTube (FlowNeuroEngine); handleApiHome() stays raw.
// `more=1`: another discovery round (continueDiscoveryFeed()), not a rebuild of the ranked mix.
// No NewPipeExtractor Page behind it: reported as boolean "hasMore", not nextPage.
internal fun RequestContext.handleApiRecommendations(
    reply: Reply,
    params: Query,
) {
    val serviceId = getServiceId(params)
    val nextPage = params.page("nextPage")
    val loadMore = params.flag("more")
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
    reply.sendJson(ranked.toSearchResult(null, hasMore))
}

internal fun RequestContext.handleApiChannel(
    reply: Reply,
    params: Query,
) {
    val serviceId = getServiceId(params)
    val channelUrl = params.required("id")
    val tab = params["tab"] ?: "videos"
    // Matches YoutubeChannelTabLinkHandlerFactory SORT_LATEST/POPULAR/OLDEST literals. YouTube "videos" tab only; no-op elsewhere.
    val sort = params["sort"]?.takeIf { it.isNotBlank() }
    val nextPage = params.page("nextPage")
    val channel = LocalServerSource.channel(dbHelper.appContext, serviceId, channelUrl, tab, sort, nextPage)
    val isSubscribed = dbHelper.isSubscribed(channel.header.url)
    reply.sendJson(
        ChannelPageDto(channel.header.toDto(isSubscribed), dbHelper.withoutHidden(channel.items).toCards(), channel.next.token()),
    )
}

internal fun RequestContext.handleApiVideo(
    reply: Reply,
    params: Query,
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
    reply.sendJson(info.toDetail(serviceId))
}

internal fun RequestContext.handleApiComments(
    reply: Reply,
    params: Query,
) {
    val serviceId = getServiceId(params)
    val videoUrl = params.required("id")
    val nextPage = params.page("nextPage")
    val page = LocalServerSource.comments(dbHelper.appContext, serviceId, videoUrl, nextPage)
    if (page.disabled) {
        reply.sendJson(CommentsDto(emptyList(), JsonNull, commentsDisabled = true))
        return
    }
    reply.sendJson(CommentsDto(page.items.map { it.toDto() }, page.next.token()?.let(::JsonPrimitive), commentsDisabled = false))
}

internal fun RequestContext.handleApiWatchProgress(
    reply: Reply,
    params: Query,
) {
    val videoUrl = params.required("id")
    val percent = params.int("percent") ?: throw BadRequestException("Missing or invalid 'percent' parameter")
    val durationSeconds = params.int("durationSeconds") ?: 0
    dbHelper.updateWatchProgress(videoUrl, percent, durationSeconds)

    // FlowNeuro WATCHED signal, best-effort; no serviceId → no-op.
    val serviceId = params.int("serviceId")
    if (serviceId != null) {
        try {
            val info = LocalServerSource.streamInfo(dbHelper.appContext, serviceId, videoUrl)
            dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.WATCHED, percent / 100f)
        } catch (e: Exception) {
            serverLog("FlowNeuro watch-signal error: " + e.message)
        }
    }

    reply.sendJson(Status("ok"))
}

// Web app endpoints (JSON).

internal fun RequestContext.handleApiHistory(reply: Reply) {
    reply.sendJson(Listing(dbHelper.historyCards()))
}

// Subscriptions + bookmarked playlists + watch-later (Library screen).
internal fun RequestContext.handleApiLibrary(reply: Reply) {
    reply.sendJson(
        LibraryDto(
            dbHelper.subscriptions().toCards(),
            dbHelper.bookmarkedPlaylists().toCards(),
            dbHelper.watchLaterItems().toCards(),
            dbHelper.userPlaylists().map { it.toCard() },
        ),
    )
}

// Subscription feed: newest uploads, one request per channel.
internal fun RequestContext.handleApiFeed(reply: Reply) {
    val items = dbHelper.withoutHidden(subscriptionFeed(dbHelper.subscriptions()))
    reply.sendJson(items.toSearchResult(null))
}

internal fun RequestContext.handleApiPlaylist(
    reply: Reply,
    params: Query,
) {
    val serviceId = getServiceId(params)
    val playlistUrl = params.required("id")
    val nextPage = params.page("nextPage")
    val playlist = LocalServerSource.playlist(dbHelper.appContext, serviceId, playlistUrl, nextPage)
    // -1 when source omits the count
    val header =
        PlaylistHeaderDto(
            playlist.header.url,
            playlist.header.name,
            playlist.header.uploaderName,
            playlist.header.streamCount.coerceAtLeast(-1),
            dbHelper.isPlaylistBookmarked(playlistUrl),
        )
    reply.sendJson(PlaylistPageDto(header, dbHelper.withoutHidden(playlist.items).toCards(), playlist.next.token()))
}

// Watch-screen button state: channel subscribed/blocked, video saved/liked/disliked.
internal fun RequestContext.handleApiState(
    reply: Reply,
    params: Query,
) {
    val videoUrl = params["id"].orEmpty()
    val channelUrl = params["channel"].orEmpty()
    val like = if (videoUrl.isEmpty()) null else dbHelper.likeState(videoUrl)
    reply.sendJson(
        ButtonState(
            subscribed = channelUrl.isNotEmpty() && dbHelper.isSubscribed(channelUrl),
            blocked = channelUrl.isNotEmpty() && dbHelper.isChannelBlocked(channelUrl),
            watchLater = videoUrl.isNotEmpty() && dbHelper.isWatchLater(videoUrl),
            like = like.orJsonNull(),
        ),
    )
}

// SponsorBlock segments (seconds): skip button + seek-bar marks; YouTube only.
internal fun RequestContext.handleApiSponsor(
    reply: Reply,
    params: Query,
) {
    val serviceId = getServiceId(params)
    val videoUrl = params["id"].orEmpty()
    val segments = mutableListOf<SponsorSegment>()
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
                segments += SponsorSegment(segment.startMs / 1000.0, segment.endMs / 1000.0, segment.category, label)
            }
        } catch (e: Exception) {
            serverLog("SponsorBlock error: " + e.message)
        }
    }
    reply.sendJson(SponsorSegments(segments))
}

// Filter settings; query params saved first.
internal fun RequestContext.handleApiSettings(
    reply: Reply,
    params: Query,
) {
    params.bool("hideWatched")?.let(dbHelper::setHideWatched)
    params.bool("hideShorts")?.let(dbHelper::setHideShorts)
    params.bool("deepFlow")?.let(dbHelper::setDeepFlow)
    val deepFlowStatus = dbHelper.deepFlow()
    reply.sendJson(SettingsDto(dbHelper.hideWatched(), dbHelper.hideShorts(), deepFlowStatus.active, deepFlowStatus.endsAt))
}
