package io.github.aedev.flow.localserver

import kotlinx.serialization.encodeToString

/** Handler on [RequestContext] (headers: [RequestContext.headers]). */
internal typealias Handler = RequestContext.(reply: Reply, params: Query) -> Unit

/** Served paths: `api` = JSON result, `page` = own reply. Whatever changes device state is [Access.OWN_PAGE]. */
internal val LOCAL_ROUTES: List<RouteSpec> =
    with(RouteTable()) {
        page("/") { reply, _ -> handleAppShell(reply) }
        page(PAIR_PATH, Access.OWN_PAGE) { reply, params -> handlePair(reply, params) }
        page("/hls", Access.CROSS_ORIGIN) { reply, params -> handleHlsProxy(reply, params) }
        page("/stream", Access.CROSS_ORIGIN) { reply, params -> handleStreamProxy(reply, params, headers) }
        page("/hlsvod", Access.CROSS_ORIGIN) { reply, params -> handleHlsVod(reply, params) }
        page("/downloadfile", Access.OWN_PAGE) { reply, params -> handleDownloadFile(reply, params, headers) }
        page("/download-cover", Access.OWN_PAGE) { reply, params -> handleDownloadCover(reply, params) }
        page("/subtitles", Access.CROSS_ORIGIN) { reply, params -> handleSubtitlesProxy(reply, params) }
        page("/thumbnails", Access.CROSS_ORIGIN) { reply, params -> handleThumbnailsProxy(reply, params) }
        page("/image-proxy", Access.CROSS_ORIGIN) { reply, params -> handleImageProxy(reply, params) }
        page("/app.css") { reply, _ -> handleAppCss(reply) }
        page("/app.js") { reply, _ -> handleAppJs(reply) }
        page(ICON_PATH) { reply, _ -> handleAppIcon(reply) }

        api("/danmaku", Access.CROSS_ORIGIN) { handleDanmaku(it) }
        api("/live_chat", Access.CROSS_ORIGIN) { handleLiveChat(it) }
        api("/handoff", Access.OWN_PAGE) { handleHandoff(it) }
        api("/send-link", Access.OWN_PAGE) { handleSendLink(it, remoteAddress) }
        api("/play", Access.OWN_PAGE) { handleSendLink(it, remoteAddress) }
        api("/remote-state", Access.OWN_PAGE) { handleRemoteState(it) }
        api("/release-lock", Access.OWN_PAGE) { handleReleaseLock(it) }
        api("/history_action", Access.OWN_PAGE) { handleHistoryAction(it) }
        api("/search-history", Access.OWN_PAGE) { handleSearchHistory(it) }
        api("/subscribe", Access.OWN_PAGE) { handleSubscribeAction(it) }
        api("/block_channel", Access.OWN_PAGE) { handleBlockChannelAction(it) }
        api("/video_feedback", Access.OWN_PAGE) { handleVideoFeedbackAction(it) }
        api("/bookmark_playlist", Access.OWN_PAGE) { handlePlaylistBookmarkAction(it) }
        api("/watch_later_action", Access.OWN_PAGE) { handleWatchLaterAction(it) }
        api("/playlist_action", Access.OWN_PAGE) { handlePlaylistAction(it) }
        api("/rate_video", Access.OWN_PAGE) { handleRateVideoAction(it) }

        api("/api/v1/search") { handleApiSearch(it) }
        api("/api/v1/home") { handleApiHome(it) }
        api("/api/v1/channel") { handleApiChannel(it) }
        api("/api/v1/video") { handleApiVideo(it) }
        api("/api/v1/comments") { handleApiComments(it) }
        api("/api/v1/watch_progress", Access.OWN_PAGE) { handleApiWatchProgress(it) }
        api("/api/v1/download", Access.OWN_PAGE) { handleApiDownload(it) }
        api("/api/v1/recommendations") { handleApiRecommendations(it) }
        api("/api/v1/cast/devices") { handleCastDevices(it) }
        api("/api/v1/cast/start", Access.OWN_PAGE) { handleCastStart(it) }
        api("/api/v1/cast/stop", Access.OWN_PAGE) { handleCastStop() }
        api("/api/v1/history") { handleApiHistory() }
        api("/api/v1/library") { handleApiLibrary() }
        api("/api/v1/downloads") { handleApiDownloads() }
        api("/api/v1/feed") { handleApiFeed(it) }
        api("/api/v1/notifications", Access.OWN_PAGE) { handleApiNotifications(it) }
        api("/api/v1/groups") { handleApiGroups() }
        api("/api/v1/playlist") { handleApiPlaylist(it) }
        api("/api/v1/playlists") { handleApiPlaylists(it) }
        api("/api/v1/liked") { handleApiLiked() }
        api("/api/v1/mylist") { handleApiMyList(it) }
        api("/api/v1/state") { handleApiState(it) }
        api("/api/v1/sponsor") { handleApiSponsor(it) }
        api("/api/v1/settings", Access.OWN_PAGE) { handleApiSettings(it) }
        api("/api/v1/profiles", Access.OWN_PAGE) { handleApiProfiles(it) }
        api("/api/v1/blocked") { handleApiBlocked() }
        api("/api/v1/dearrow", Access.OWN_PAGE) { handleApiDeArrow(it) }
        api("/api/v1/dislikes", Access.OWN_PAGE) { handleApiDislikes(it) }
        api("/api/v1/ailist", Access.OWN_PAGE) { handleApiAiList(it) }
        api("/api/v1/sponsorblock", Access.OWN_PAGE) { handleApiSponsorBlock(it) }
        api("/api/v1/wellbeing", Access.OWN_PAGE) { handleApiWellbeing(it) }

        page("/vendor/{file...}") { reply, _ -> handleVendorAsset(reply, requestPath) }
        page("/fonts/{file...}") { reply, _ -> handleAppFont(reply, requestPath) }
        page("/{path...}") { reply, _ -> reply.send(404, "Page Not Found", "text/plain; charset=UTF-8") }
        specs
    }

/** Failure body + content type: JSON on JSON routes, else text; 500 hides the message. */
internal fun failureBody(
    json: Boolean,
    code: Int,
    message: String?,
): Pair<String, String> =
    when {
        json -> ApiJson.encodeToString(ErrorBody(error = message ?: "Unknown error")) to "application/json"
        code == 400 -> "Bad Request: $message" to "text/plain; charset=UTF-8"
        else -> "Internal Server Error" to "text/plain; charset=UTF-8"
    }
