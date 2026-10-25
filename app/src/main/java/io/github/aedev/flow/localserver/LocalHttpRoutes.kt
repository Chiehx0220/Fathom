package io.github.aedev.flow.localserver

import io.ktor.server.routing.Route
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.encodeToString

/** Handler on [RequestContext] (headers: [RequestContext.headers]). */
internal typealias Handler = RequestContext.(reply: Reply, params: Query) -> Unit

/** Served paths: `api` = JSON result, `page` = own reply. */
internal fun Route.localRoutes(blocking: CoroutineDispatcher) =
    with(RouteTable(this, blocking)) {
        page("/") { reply, _ -> handleAppShell(reply) }
        page(PAIR_PATH) { reply, params -> handlePair(reply, params) }
        page("/hls") { reply, params -> handleHlsProxy(reply, params) }
        page("/stream") { reply, params -> handleStreamProxy(reply, params, headers) }
        page("/hlsvod") { reply, params -> handleHlsVod(reply, params) }
        page("/downloadfile") { reply, params -> handleDownloadFile(reply, params, headers) }
        page("/subtitles") { reply, params -> handleSubtitlesProxy(reply, params) }
        page("/thumbnails") { reply, params -> handleThumbnailsProxy(reply, params) }
        page("/image-proxy") { reply, params -> handleImageProxy(reply, params) }
        page("/app.css") { reply, _ -> handleAppCss(reply) }
        page("/app.js") { reply, _ -> handleAppJs(reply) }
        page(ICON_PATH) { reply, _ -> handleAppIcon(reply) }

        api("/danmaku") { handleDanmaku(it) }
        api("/live_chat") { handleLiveChat(it) }
        api("/handoff") { handleHandoff(it) }
        api("/send-link") { handleSendLink(it, remoteAddress) }
        api("/play") { handleSendLink(it, remoteAddress) }
        api("/remote-state") { handleRemoteState(it) }
        api("/release-lock") { handleReleaseLock(it) }
        api("/history_action") { handleHistoryAction(it) }
        api("/search-history") { handleSearchHistory(it) }
        api("/subscribe") { handleSubscribeAction(it) }
        api("/block_channel") { handleBlockChannelAction(it) }
        api("/bookmark_playlist") { handlePlaylistBookmarkAction(it) }
        api("/watch_later_action") { handleWatchLaterAction(it) }
        api("/playlist_action") { handlePlaylistAction(it) }
        api("/rate_video") { handleRateVideoAction(it) }

        api("/api/v1/search") { handleApiSearch(it) }
        api("/api/v1/home") { handleApiHome(it) }
        api("/api/v1/channel") { handleApiChannel(it) }
        api("/api/v1/video") { handleApiVideo(it) }
        api("/api/v1/comments") { handleApiComments(it) }
        api("/api/v1/watch_progress") { handleApiWatchProgress(it) }
        api("/api/v1/download") { handleApiDownload(it) }
        api("/api/v1/recommendations") { handleApiRecommendations(it) }
        api("/api/v1/cast/devices") { handleCastDevices(it) }
        api("/api/v1/cast/start") { handleCastStart(it) }
        api("/api/v1/cast/stop") { handleCastStop() }
        api("/api/v1/history") { handleApiHistory() }
        api("/api/v1/library") { handleApiLibrary() }
        api("/api/v1/downloads") { handleApiDownloads() }
        api("/api/v1/feed") { handleApiFeed() }
        api("/api/v1/playlist") { handleApiPlaylist(it) }
        api("/api/v1/playlists") { handleApiPlaylists(it) }
        api("/api/v1/liked") { handleApiLiked() }
        api("/api/v1/mylist") { handleApiMyList(it) }
        api("/api/v1/state") { handleApiState(it) }
        api("/api/v1/sponsor") { handleApiSponsor(it) }
        api("/api/v1/settings") { handleApiSettings(it) }

        page("/vendor/{file...}") { reply, _ -> handleVendorAsset(reply, requestPath) }
        page("/{path...}") { reply, _ -> reply.send(404, "Page Not Found", "text/plain; charset=UTF-8") }
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
