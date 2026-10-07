package io.github.aedev.flow.localserver

import java.io.OutputStream

/** A request handler: runs on the connection's [ClientHandler] with the response stream, the query and the request headers. */
internal typealias Route = ClientHandler.(os: OutputStream, params: Map<String, String>, requestHeaders: Map<String, String>) -> Unit

/** Every path the server answers, built once. */
internal val ROUTES: Map<String, Route> =
    mapOf(
        "/" to route { os, _, _ -> handleAppShell(os) },
        "/danmaku" to route { os, params, _ -> handleDanmaku(os, params) },
        "/live_chat" to route { os, params, _ -> handleLiveChat(os, params) },
        "/hls" to route { os, params, _ -> handleHlsProxy(os, params) },
        "/send-link" to route { os, params, _ -> handleSendLink(os, params, remoteAddress) },
        "/play" to route { os, params, _ -> handleSendLink(os, params, remoteAddress) },
        "/send-command" to route { os, params, _ -> handleSendCommand(os, params) },
        "/poll-commands" to route { os, _, _ -> handlePollCommands(os) },
        "/remote-state" to route { os, params, _ -> handleRemoteState(os, params) },
        "/release-lock" to route { os, params, _ -> handleReleaseLock(os, params) },
        "/history_action" to route { os, params, _ -> handleHistoryAction(os, params) },
        "/stream" to route { os, params, requestHeaders -> handleStreamProxy(os, params, requestHeaders) },
        "/manifest" to route { os, params, _ -> handleManifestProxy(os, params) },
        "/hlsvod" to route { os, params, _ -> handleHlsVod(os, params) },
        "/subtitles" to route { os, params, _ -> handleSubtitlesProxy(os, params) },
        "/thumbnails" to route { os, params, _ -> handleThumbnailsProxy(os, params) },
        "/image-proxy" to route { os, params, _ -> handleImageProxy(os, params) },
        "/api/player/play" to route { os, params, _ -> handleApiPlayerPlay(os, params) },
        "/api/player/pause" to route { os, _, _ -> handleApiPlayerPause(os) },
        "/api/player/resume" to route { os, _, _ -> handleApiPlayerResume(os) },
        "/api/player/stop" to route { os, _, _ -> handleApiPlayerStop(os) },
        "/search-history" to route { os, params, _ -> handleSearchHistory(os, params) },
        "/subscribe" to route { os, params, _ -> handleSubscribeAction(os, params) },
        "/block_channel" to route { os, params, _ -> handleBlockChannelAction(os, params) },
        "/bookmark_playlist" to route { os, params, _ -> handlePlaylistBookmarkAction(os, params) },
        "/watch_later_action" to route { os, params, _ -> handleWatchLaterAction(os, params) },
        "/rate_video" to route { os, params, _ -> handleRateVideoAction(os, params) },
        "/api/v1/search" to route { os, params, _ -> handleApiSearch(os, params) },
        "/api/v1/home" to route { os, params, _ -> handleApiHome(os, params) },
        "/api/v1/channel" to route { os, params, _ -> handleApiChannel(os, params) },
        "/api/v1/video" to route { os, params, _ -> handleApiVideo(os, params) },
        "/api/v1/comments" to route { os, params, _ -> handleApiComments(os, params) },
        "/api/v1/watch_progress" to route { os, params, _ -> handleApiWatchProgress(os, params) },
        "/api/v1/download" to route { os, params, _ -> handleApiDownload(os, params) },
        "/api/v1/bilibili_probe" to route { os, params, _ -> handleApiBilibiliProbe(os, params) },
        "/api/v1/recommendations" to route { os, params, _ -> handleApiRecommendations(os, params) },
        "/api/v1/ping" to route { os, _, _ -> handleApiPing(os) },
        "/api/v1/history" to route { os, _, _ -> handleApiHistory(os) },
        "/api/v1/library" to route { os, params, _ -> handleApiLibrary(os, params) },
        "/api/v1/feed" to route { os, params, _ -> handleApiFeed(os, params) },
        "/api/v1/playlist" to route { os, params, _ -> handleApiPlaylist(os, params) },
        "/api/v1/state" to route { os, params, _ -> handleApiState(os, params) },
        "/api/v1/sponsor" to route { os, params, _ -> handleApiSponsor(os, params) },
        "/api/v1/settings" to route { os, params, _ -> handleApiSettings(os, params) },
        "/app.css" to route { os, _, _ -> handleAppCss(os) },
        "/app.js" to route { os, _, _ -> handleAppJs(os) },
    )

private fun route(handler: Route): Route = handler
