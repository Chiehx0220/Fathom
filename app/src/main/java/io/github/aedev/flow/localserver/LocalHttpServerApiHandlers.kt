package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.recommendation.InteractionType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page

// `/api/v1/...` handlers.

internal fun RequestContext.handleApiSearch(params: Query): SearchResult {
    val serviceId = getServiceId(params)
    val query = params.required("q")
    val nextPage = params.page("nextPage")
    if (nextPage == null) dbHelper.addSearchQuery(query)
    val page = source.search(serviceId, query, nextPage)
    return dbHelper.withoutHidden(page.items).toSearchResult(page.next)
}

internal fun RequestContext.handleApiHome(params: Query): SearchResult {
    val serviceId = getServiceId(params)
    val nextPage = params.page("nextPage")
    val items: List<InfoItem>
    val next: Page?
    if (serviceId != LocalServerMedia.SERVICE_YOUTUBE) {
        val page = source.home(serviceId, nextPage)
        items = page.items
        next = page.next
    } else {
        // fetchTrendingItems(): no pagination.
        items = source.trending(serviceId)
        next = null
    }
    return dbHelper.withoutHidden(items).toSearchResult(next)
}

// Ranked home (YouTube: FlowNeuro); `more=1` = next discovery round; `hasMore` replaces nextPage (no Page behind it).
internal fun RequestContext.handleApiRecommendations(params: Query): SearchResult {
    val serviceId = getServiceId(params)
    val nextPage = params.page("nextPage")
    val loadMore = params.flag("more")
    var items: List<InfoItem>
    var alreadyRanked = false
    var hasMore = false
    if (serviceId != LocalServerMedia.SERVICE_YOUTUBE) {
        // Not pre-ranked: ranked below; service nextPage covers `more`.
        val page = source.home(serviceId, nextPage)
        items = page.items
        hasMore = page.next != null
    } else if (loadMore) {
        val (feedItems, more) = dbHelper.continueDiscoveryFeed(serviceId)
        items = feedItems
        alreadyRanked = true
        hasMore = more
    } else {
        // Trending + discovery + subs, ranked; later rounds via continueDiscoveryFeed().
        val (feedItems, more) = dbHelper.buildAndRankHomeFeed(serviceId)
        items = feedItems
        alreadyRanked = true
        hasMore = more
    }
    val filtered = dbHelper.withoutHidden(items)
    val ranked = if (alreadyRanked) filtered else dbHelper.rankWithFlowNeuro(filtered, serviceId)
    return ranked.toSearchResult(null, hasMore)
}

internal fun RequestContext.handleApiChannel(params: Query): ChannelPageDto {
    val serviceId = getServiceId(params)
    val channelUrl = params.required("id")
    val tab = params["tab"] ?: "videos"
    // YoutubeChannelTabLinkHandlerFactory SORT_* literals; YouTube videos tab only.
    val sort = params["sort"]?.takeIf { it.isNotBlank() }
    val nextPage = params.page("nextPage")
    val channel = source.channel(serviceId, channelUrl, tab, sort, nextPage)
    val isSubscribed = dbHelper.isSubscribed(channel.header.url)
    return ChannelPageDto(channel.header.toDto(isSubscribed), dbHelper.withoutHidden(channel.items).toCards(), channel.next.token())
}

internal fun RequestContext.handleApiVideo(params: Query): VideoDetail {
    val serviceId = getServiceId(params)
    val info = source.watchInfo(serviceId, params.required("id"))
    val thumbUrl =
        info.thumbnails
            ?.lastOrNull()
            ?.url
            .orEmpty()
    val uploaderAvatarUrl = info.uploaderAvatars?.takeIf { it.isNotEmpty() }?.let { WebFormat.getThumbnailUrl(it) }
    dbHelper.saveToHistory(info.name, info.url, info.uploaderName, thumbUrl, serviceId, info.uploaderUrl, uploaderAvatarUrl)
    // FlowNeuro click, best-effort.
    dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.CLICK)
    return info.toDetail(serviceId)
}

internal fun RequestContext.handleApiComments(params: Query): CommentsDto {
    val serviceId = getServiceId(params)
    val videoUrl = params.required("id")
    val nextPage = params.page("nextPage")
    val page = source.comments(serviceId, videoUrl, nextPage)
    if (page.disabled) {
        return CommentsDto(emptyList(), JsonNull, commentsDisabled = true)
    }
    return CommentsDto(page.items.map { it.toDto() }, page.next.token()?.let(::JsonPrimitive), commentsDisabled = false)
}

internal fun RequestContext.handleApiWatchProgress(params: Query): Status {
    val videoUrl = params.required("id")
    val percent = params.int("percent") ?: throw BadRequestException("Missing or invalid 'percent' parameter")
    val durationSeconds = params.int("durationSeconds") ?: 0
    dbHelper.updateWatchProgress(videoUrl, percent, durationSeconds)

    // FlowNeuro WATCHED signal, best-effort; no serviceId → no-op.
    val serviceId = params.int("serviceId")
    if (serviceId != null) {
        try {
            val info = source.streamInfo(serviceId, videoUrl)
            dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.WATCHED, percent / 100f)
        } catch (e: Exception) {
            serverLog("FlowNeuro watch-signal error: " + e.message)
        }
    }

    return Status("ok")
}

internal fun RequestContext.handleApiHistory() = Listing(dbHelper.historyCards())

// Subscriptions + bookmarked playlists + watch-later (Library screen).
internal fun RequestContext.handleApiLibrary(): LibraryDto =
    LibraryDto(
        dbHelper.subscriptions().toCards(),
        dbHelper.bookmarkedPlaylists().toCards(),
        dbHelper.watchLaterItems().toCards(),
        dbHelper.userPlaylists().map { it.toCard() },
    )

// Subscription feed: newest uploads, one request per channel.
internal fun RequestContext.handleApiFeed(): SearchResult {
    val items = dbHelper.withoutHidden(subscriptionFeed(dbHelper.subscriptions()))
    return items.toSearchResult(null)
}

internal fun RequestContext.handleApiPlaylist(params: Query): PlaylistPageDto {
    val serviceId = getServiceId(params)
    val playlistUrl = params.required("id")
    val nextPage = params.page("nextPage")
    val playlist = source.playlist(serviceId, playlistUrl, nextPage)
    // -1 when source omits the count
    val header =
        PlaylistHeaderDto(
            playlist.header.url,
            playlist.header.name,
            playlist.header.uploaderName,
            playlist.header.streamCount.coerceAtLeast(-1),
            dbHelper.isPlaylistBookmarked(playlistUrl),
        )
    return PlaylistPageDto(header, dbHelper.withoutHidden(playlist.items).toCards(), playlist.next.token())
}

// Watch-screen button state: channel subscribed/blocked, video saved/liked/disliked.
internal fun RequestContext.handleApiState(params: Query): ButtonState {
    val videoUrl = params["id"].orEmpty()
    val channelUrl = params["channel"].orEmpty()
    val like = if (videoUrl.isEmpty()) null else dbHelper.likeState(videoUrl)
    return ButtonState(
        subscribed = channelUrl.isNotEmpty() && dbHelper.isSubscribed(channelUrl),
        blocked = channelUrl.isNotEmpty() && dbHelper.isChannelBlocked(channelUrl),
        watchLater = videoUrl.isNotEmpty() && dbHelper.isWatchLater(videoUrl),
        like = like.orJsonNull(),
    )
}

// SponsorBlock segments (seconds): skip button + seek-bar marks; YouTube only.
internal fun RequestContext.handleApiSponsor(params: Query): SponsorSegments {
    val serviceId = getServiceId(params)
    val videoUrl = params["id"].orEmpty()
    val segments = mutableListOf<SponsorSegment>()
    if (serviceId == LocalServerMedia.SERVICE_YOUTUBE && videoUrl.isNotEmpty()) {
        try {
            for (segment in source.sponsorSegments(LocalServerMedia.getVideoId(videoUrl))) {
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
    return SponsorSegments(segments)
}

// Filter settings; query params saved first.
internal fun RequestContext.handleApiSettings(params: Query): SettingsDto {
    params.bool("hideWatched")?.let(dbHelper::setHideWatched)
    params.bool("hideShorts")?.let(dbHelper::setHideShorts)
    params.bool("deepFlow")?.let(dbHelper::setDeepFlow)
    val deepFlowStatus = dbHelper.deepFlow()
    return SettingsDto(dbHelper.hideWatched(), dbHelper.hideShorts(), deepFlowStatus.active, deepFlowStatus.endsAt)
}
