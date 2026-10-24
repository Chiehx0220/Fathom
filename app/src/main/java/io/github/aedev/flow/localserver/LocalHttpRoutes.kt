package io.github.aedev.flow.localserver

/** Handler on [ClientHandler]: reply, query, request headers. */
internal typealias Route = ClientHandler.(reply: Reply, params: Map<String, String>, requestHeaders: Map<String, String>) -> Unit

/** All served paths, built once. */
internal val ROUTES: Map<String, Route> =
    mapOf(
        "/" to route { reply, _, _ -> handleAppShell(reply) },
        PAIR_PATH to route { reply, params, _ -> handlePair(reply, params) },
        "/danmaku" to route { reply, params, _ -> handleDanmaku(reply, params) },
        "/live_chat" to route { reply, params, _ -> handleLiveChat(reply, params) },
        "/hls" to route { reply, params, _ -> handleHlsProxy(reply, params) },
        "/handoff" to route { reply, params, _ -> handleHandoff(reply, params) },
        "/send-link" to route { reply, params, _ -> handleSendLink(reply, params, remoteAddress) },
        "/play" to route { reply, params, _ -> handleSendLink(reply, params, remoteAddress) },
        "/remote-state" to route { reply, params, _ -> handleRemoteState(reply, params) },
        "/release-lock" to route { reply, params, _ -> handleReleaseLock(reply, params) },
        "/history_action" to route { reply, params, _ -> handleHistoryAction(reply, params) },
        "/stream" to route { reply, params, requestHeaders -> handleStreamProxy(reply, params, requestHeaders) },
        "/hlsvod" to route { reply, params, _ -> handleHlsVod(reply, params) },
        "/downloadfile" to route { reply, params, requestHeaders -> handleDownloadFile(reply, params, requestHeaders) },
        "/subtitles" to route { reply, params, _ -> handleSubtitlesProxy(reply, params) },
        "/thumbnails" to route { reply, params, _ -> handleThumbnailsProxy(reply, params) },
        "/image-proxy" to route { reply, params, _ -> handleImageProxy(reply, params) },
        "/search-history" to route { reply, params, _ -> handleSearchHistory(reply, params) },
        "/subscribe" to route { reply, params, _ -> handleSubscribeAction(reply, params) },
        "/block_channel" to route { reply, params, _ -> handleBlockChannelAction(reply, params) },
        "/bookmark_playlist" to route { reply, params, _ -> handlePlaylistBookmarkAction(reply, params) },
        "/watch_later_action" to route { reply, params, _ -> handleWatchLaterAction(reply, params) },
        "/playlist_action" to route { reply, params, _ -> handlePlaylistAction(reply, params) },
        "/rate_video" to route { reply, params, _ -> handleRateVideoAction(reply, params) },
        "/api/v1/search" to route { reply, params, _ -> handleApiSearch(reply, params) },
        "/api/v1/home" to route { reply, params, _ -> handleApiHome(reply, params) },
        "/api/v1/channel" to route { reply, params, _ -> handleApiChannel(reply, params) },
        "/api/v1/video" to route { reply, params, _ -> handleApiVideo(reply, params) },
        "/api/v1/comments" to route { reply, params, _ -> handleApiComments(reply, params) },
        "/api/v1/watch_progress" to route { reply, params, _ -> handleApiWatchProgress(reply, params) },
        "/api/v1/download" to route { reply, params, _ -> handleApiDownload(reply, params) },
        "/api/v1/recommendations" to route { reply, params, _ -> handleApiRecommendations(reply, params) },
        "/api/v1/cast/devices" to route { reply, params, _ -> handleCastDevices(reply, params) },
        "/api/v1/cast/start" to route { reply, params, _ -> handleCastStart(reply, params) },
        "/api/v1/cast/stop" to route { reply, _, _ -> handleCastStop(reply) },
        "/api/v1/history" to route { reply, _, _ -> handleApiHistory(reply) },
        "/api/v1/library" to route { reply, params, _ -> handleApiLibrary(reply, params) },
        "/api/v1/downloads" to route { reply, _, _ -> handleApiDownloads(reply) },
        "/api/v1/feed" to route { reply, params, _ -> handleApiFeed(reply, params) },
        "/api/v1/playlist" to route { reply, params, _ -> handleApiPlaylist(reply, params) },
        "/api/v1/playlists" to route { reply, params, _ -> handleApiPlaylists(reply, params) },
        "/api/v1/liked" to route { reply, _, _ -> handleApiLiked(reply) },
        "/api/v1/mylist" to route { reply, params, _ -> handleApiMyList(reply, params) },
        "/api/v1/state" to route { reply, params, _ -> handleApiState(reply, params) },
        "/api/v1/sponsor" to route { reply, params, _ -> handleApiSponsor(reply, params) },
        "/api/v1/settings" to route { reply, params, _ -> handleApiSettings(reply, params) },
        "/app.css" to route { reply, _, _ -> handleAppCss(reply) },
        "/app.js" to route { reply, _, _ -> handleAppJs(reply) },
        ICON_PATH to route { reply, _, _ -> handleAppIcon(reply) },
    )

/** Non-`/api/` JSON routes; failures are JSON. */
internal val JSON_ROUTES: Set<String> =
    setOf(
        "/danmaku",
        "/live_chat",
        "/handoff",
        "/send-link",
        "/play",
        "/remote-state",
        "/release-lock",
        "/history_action",
        "/search-history",
        "/subscribe",
        "/block_channel",
        "/bookmark_playlist",
        "/watch_later_action",
        "/playlist_action",
        "/rate_video",
    )

/** Failure body + content type: JSON on JSON routes, else text; 500 hides the message. */
internal fun failureBody(
    path: String,
    code: Int,
    message: String?,
): Pair<String, String> =
    when {
        path.startsWith("/api/") || path in JSON_ROUTES -> ApiRenderer.errorJson(message) to "application/json"
        code == 400 -> "Bad Request: $message" to "text/plain; charset=UTF-8"
        else -> "Internal Server Error" to "text/plain; charset=UTF-8"
    }

private fun route(handler: Route): Route = handler
