package io.github.aedev.flow.localserver

import io.ktor.server.routing.Route
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.encodeToString

/** Handler on [RequestContext]: reply, query, request headers (lower-cased names). */
internal typealias Handler = RequestContext.(reply: Reply, params: Query, requestHeaders: Map<String, String>) -> Unit

/** Served paths; `json` = JSON failure body, else text. */
internal fun Route.localRoutes(blocking: CoroutineDispatcher) {
    fun page(
        path: String,
        handler: Handler,
    ) = serve(path, blocking, json = false, handler)

    fun json(
        path: String,
        handler: Handler,
    ) = serve(path, blocking, json = true, handler)

    page("/") { reply, _, _ -> handleAppShell(reply) }
    page(PAIR_PATH) { reply, params, _ -> handlePair(reply, params) }
    page("/hls") { reply, params, _ -> handleHlsProxy(reply, params) }
    page("/stream") { reply, params, requestHeaders -> handleStreamProxy(reply, params, requestHeaders) }
    page("/hlsvod") { reply, params, _ -> handleHlsVod(reply, params) }
    page("/downloadfile") { reply, params, requestHeaders -> handleDownloadFile(reply, params, requestHeaders) }
    page("/subtitles") { reply, params, _ -> handleSubtitlesProxy(reply, params) }
    page("/thumbnails") { reply, params, _ -> handleThumbnailsProxy(reply, params) }
    page("/image-proxy") { reply, params, _ -> handleImageProxy(reply, params) }
    page("/app.css") { reply, _, _ -> handleAppCss(reply) }
    page("/app.js") { reply, _, _ -> handleAppJs(reply) }
    page(ICON_PATH) { reply, _, _ -> handleAppIcon(reply) }

    json("/danmaku") { reply, params, _ -> handleDanmaku(reply, params) }
    json("/live_chat") { reply, params, _ -> handleLiveChat(reply, params) }
    json("/handoff") { reply, params, _ -> handleHandoff(reply, params) }
    json("/send-link") { reply, params, _ -> handleSendLink(reply, params, remoteAddress) }
    json("/play") { reply, params, _ -> handleSendLink(reply, params, remoteAddress) }
    json("/remote-state") { reply, params, _ -> handleRemoteState(reply, params) }
    json("/release-lock") { reply, params, _ -> handleReleaseLock(reply, params) }
    json("/history_action") { reply, params, _ -> handleHistoryAction(reply, params) }
    json("/search-history") { reply, params, _ -> handleSearchHistory(reply, params) }
    json("/subscribe") { reply, params, _ -> handleSubscribeAction(reply, params) }
    json("/block_channel") { reply, params, _ -> handleBlockChannelAction(reply, params) }
    json("/bookmark_playlist") { reply, params, _ -> handlePlaylistBookmarkAction(reply, params) }
    json("/watch_later_action") { reply, params, _ -> handleWatchLaterAction(reply, params) }
    json("/playlist_action") { reply, params, _ -> handlePlaylistAction(reply, params) }
    json("/rate_video") { reply, params, _ -> handleRateVideoAction(reply, params) }

    json("/api/v1/search") { reply, params, _ -> handleApiSearch(reply, params) }
    json("/api/v1/home") { reply, params, _ -> handleApiHome(reply, params) }
    json("/api/v1/channel") { reply, params, _ -> handleApiChannel(reply, params) }
    json("/api/v1/video") { reply, params, _ -> handleApiVideo(reply, params) }
    json("/api/v1/comments") { reply, params, _ -> handleApiComments(reply, params) }
    json("/api/v1/watch_progress") { reply, params, _ -> handleApiWatchProgress(reply, params) }
    json("/api/v1/download") { reply, params, _ -> handleApiDownload(reply, params) }
    json("/api/v1/recommendations") { reply, params, _ -> handleApiRecommendations(reply, params) }
    json("/api/v1/cast/devices") { reply, params, _ -> handleCastDevices(reply, params) }
    json("/api/v1/cast/start") { reply, params, _ -> handleCastStart(reply, params) }
    json("/api/v1/cast/stop") { reply, _, _ -> handleCastStop(reply) }
    json("/api/v1/history") { reply, _, _ -> handleApiHistory(reply) }
    json("/api/v1/library") { reply, _, _ -> handleApiLibrary(reply) }
    json("/api/v1/downloads") { reply, _, _ -> handleApiDownloads(reply) }
    json("/api/v1/feed") { reply, _, _ -> handleApiFeed(reply) }
    json("/api/v1/playlist") { reply, params, _ -> handleApiPlaylist(reply, params) }
    json("/api/v1/playlists") { reply, params, _ -> handleApiPlaylists(reply, params) }
    json("/api/v1/liked") { reply, _, _ -> handleApiLiked(reply) }
    json("/api/v1/mylist") { reply, params, _ -> handleApiMyList(reply, params) }
    json("/api/v1/state") { reply, params, _ -> handleApiState(reply, params) }
    json("/api/v1/sponsor") { reply, params, _ -> handleApiSponsor(reply, params) }
    json("/api/v1/settings") { reply, params, _ -> handleApiSettings(reply, params) }

    page("/vendor/{file...}") { reply, _, _ -> handleVendorAsset(reply, requestPath) }
    page("/{path...}") { reply, _, _ -> reply.send(404, "Page Not Found", "text/plain; charset=UTF-8") }
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
