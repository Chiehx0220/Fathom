package org.schabi.newpipe.localserver

import org.schabi.newpipe.localserver.LocalHttpServer.ClientHandler
import java.io.OutputStream
import java.util.UUID

// The toggle-style DB action endpoints (subscribe, block channel, bookmark playlist, history,
// watch-later, like/dislike) and the TV remote-control endpoints (send-link, release-lock,
// send-command, poll-commands), split out of LocalHttpServer.kt as extension functions on
// ClientHandler purely to keep that file from growing without bound - no behavior changed by the
// move. Needs ClientHandler/dbHelper/sendResponse/sendRedirect/getServiceId to be `internal`
// rather than `private` in LocalHttpServer.kt for these to reach them from a different file; the
// companion object functions called below (getActiveLockCode, tryLock, addPendingCommand, log,
// etc.) were already non-private.

// The url/title/uploader/thumbnail/uploaderUrl bundle a "share this video" request carries -
// identical field set and fallback defaults in handleWatchLaterAction and handleRateVideoAction
// (both cover the same "server never saw this video before, all we know is what the client's own
// page already had on screen" case).
private data class SharedVideoParams(val title: String, val uploader: String, val thumbnail: String?, val uploaderUrl: String?)

private fun parseSharedVideoParams(params: Map<String, String>): SharedVideoParams {
    val title = params["title"].takeUnless { it.isNullOrEmpty() } ?: "Shared Item"
    val uploader = params["uploader"] ?: ""
    val thumbnail = params["thumbnail"]
    val uploaderUrl = params["uploaderUrl"]
    return SharedVideoParams(title, uploader, thumbnail, uploaderUrl)
}

// Every toggle-style action handler (subscribe, block channel, watch-later, like/dislike, history
// remove/clear, playlist bookmark) ends with the same "how do I answer this" call: an ajax caller
// wants a bare JSON ack, a caller that already has a real page path wants a redirect back to it,
// and a caller that only passed a bare video id (the older ?...&back=<videoId> shape some of
// these endpoints still accept) wants a /watch redirect built from it. [fallbackPath] is used when
// the caller passed no `back` at all. Was hand-copied into 4 of these handlers verbatim before
// this - one word (the fallback path) was the only thing that ever differed between copies.
private fun ClientHandler.sendActionResult(os: OutputStream, serviceId: Int, back: String?, fallbackPath: String) {
    when {
        "ajax" == back -> sendResponse(os, 200, "{\"status\":\"success\"}", "application/json")
        back.isNullOrEmpty() -> sendRedirect(os, fallbackPath)
        back.startsWith("/") -> sendRedirect(os, back)
        else -> sendRedirect(os, "/watch?serviceId=" + serviceId + "&id=" + java.net.URLEncoder.encode(back, "UTF-8"))
    }
}

internal fun ClientHandler.handleSubscribeAction(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val action = params["action"]
    val channelUrl = params["id"]
    val back = params["back"]

    if ("subscribe" == action && !channelUrl.isNullOrEmpty()) {
        val name = params["name"]
        val avatar = params["avatar"]
        dbHelper.nativeAddSubscription(channelUrl, name, avatar)
    } else if ("unsubscribe" == action && !channelUrl.isNullOrEmpty()) {
        dbHelper.nativeRemoveSubscription(channelUrl)
    }

    sendActionResult(os, serviceId, back, "/subscriptions?serviceId=$serviceId")
}

internal fun ClientHandler.handleBlockChannelAction(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val action = params["action"]
    val channelUrl = params["id"]
    val back = params["back"]

    if ("block" == action && !channelUrl.isNullOrEmpty()) {
        dbHelper.nativeBlockChannel(channelUrl)
    } else if ("unblock" == action && !channelUrl.isNullOrEmpty()) {
        dbHelper.nativeUnblockChannel(channelUrl)
    }

    sendActionResult(os, serviceId, back, "/?serviceId=$serviceId")
}

internal fun ClientHandler.handlePlaylistBookmarkAction(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val action = params["action"]
    val playlistUrl = params["id"]
    val back = params["back"]

    if ("bookmark" == action && !playlistUrl.isNullOrEmpty()) {
        val name = params["name"]
        dbHelper.nativeBookmarkPlaylist(playlistUrl, name, null)
    } else if ("unbookmark" == action && !playlistUrl.isNullOrEmpty()) {
        dbHelper.nativeUnbookmarkPlaylist(playlistUrl)
    }

    sendActionResult(os, serviceId, back, "/subscriptions?serviceId=$serviceId&tab=playlists")
}

internal fun ClientHandler.handleHistoryAction(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val action = params["action"]
    val url = params["url"]
    val back = params["back"]

    if ("remove" == action && !url.isNullOrEmpty()) {
        dbHelper.nativeRemoveFromHistory(url)
    } else if ("clear" == action) {
        dbHelper.nativeClearHistory()
    }

    sendActionResult(os, serviceId, back, "/history?serviceId=$serviceId")
}

internal fun ClientHandler.handleWatchLaterAction(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val action = params["action"]
    val url = params["url"]
    val back = params["back"]

    if ("add" == action && !url.isNullOrEmpty()) {
        val meta = parseSharedVideoParams(params)
        dbHelper.nativeAddWatchLater(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl)
    } else if ("remove" == action && !url.isNullOrEmpty()) {
        dbHelper.nativeRemoveWatchLater(url)
    }

    sendActionResult(os, serviceId, back, "/watch-later?serviceId=$serviceId")
}

internal fun ClientHandler.handleRateVideoAction(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val action = params["action"]
    val url = params["url"]
    val back = params["back"]

    if (!url.isNullOrEmpty()) {
        val meta = parseSharedVideoParams(params)
        when (action) {
            "like" -> dbHelper.nativeLikeVideo(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl, serviceId)
            "dislike" -> dbHelper.nativeDislikeVideo(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl)
            "remove" -> dbHelper.nativeRemoveLikeState(url)
        }
    }

    sendActionResult(os, serviceId, back, "/?serviceId=$serviceId")
}

internal fun ClientHandler.handleSendLink(os: OutputStream, params: Map<String, String>, clientIp: String?) {
    val videoUrl = params["id"]
    val clientReleaseCode = params["release_code"]
    var videoTitle = params["title"]
    if (videoTitle.isNullOrEmpty()) {
        videoTitle = "Video"
    }

    if (videoUrl.isNullOrEmpty()) {
        sendResponse(os, 400, "{\"status\":\"error\",\"message\":\"Missing 'id' parameter\"}", "application/json; charset=UTF-8")
        return
    }

    synchronized(LocalHttpServer::class.java) {
        var hasLock = false
        var currentLockCode = LocalHttpServer.getActiveLockCode()

        if (currentLockCode == null) {
            val newLockCode = UUID.randomUUID().toString()
            LocalHttpServer.tryLock(newLockCode, clientIp ?: "", videoTitle)
            currentLockCode = newLockCode
            hasLock = true
        } else if (currentLockCode == clientReleaseCode) {
            LocalHttpServer.tryLock(currentLockCode, clientIp ?: "", videoTitle)
            hasLock = true
        }

        if (hasLock) {
            // "__connect_only__" is sent by the header's cast button when the current page isn't a
            // video (e.g. Home, Subscriptions) - it pairs this device as remote-controllable
            // without queuing a bogus play_video command for a non-video URL, which used to make
            // the connecting browser navigate itself to a broken /watch link (see RemoteActivity's
            // touchpad flow: pairing must work from any page).
            if ("__connect_only__" != videoUrl) {
                LocalHttpServer.log("Casting link: $videoUrl from client IP $clientIp")
                LocalHttpServer.addPendingCommand("play_video:$videoUrl")
            } else {
                LocalHttpServer.log("Remote connected (pairing only) from client IP $clientIp")
            }

            val json = "{\"status\":\"success\",\"release_code\":\"" + currentLockCode + "\"}"
            sendResponse(os, 200, json, "application/json; charset=UTF-8")
        } else {
            var busyMsg = "Server is currently controlled by device at IP " + LocalHttpServer.getActiveClientIp()
            if (LocalHttpServer.getActiveVideoTitle() != null) {
                busyMsg += " playing: " + LocalHttpServer.getActiveVideoTitle()
            }
            val json = "{\"status\":\"busy\",\"message\":\"" + busyMsg.replace("\"", "\\\"") + "\"}"
            sendResponse(os, 200, json, "application/json; charset=UTF-8")
        }
    }
}

internal fun ClientHandler.handleReleaseLock(os: OutputStream, params: Map<String, String>) {
    val clientReleaseCode = params["release_code"]
    if (clientReleaseCode.isNullOrEmpty()) {
        sendResponse(os, 400, "{\"status\":\"error\",\"message\":\"Missing 'release_code' parameter\"}", "application/json; charset=UTF-8")
        return
    }

    synchronized(LocalHttpServer::class.java) {
        val currentLockCode = LocalHttpServer.getActiveLockCode()
        if (currentLockCode != null && currentLockCode == clientReleaseCode) {
            LocalHttpServer.releaseLock()
            LocalHttpServer.log("Lock released by client.")
            sendResponse(os, 200, "{\"status\":\"success\"}", "application/json; charset=UTF-8")
        } else {
            sendResponse(os, 200, "{\"status\":\"error\",\"message\":\"Invalid or expired lock code\"}", "application/json; charset=UTF-8")
        }
    }
}

internal fun ClientHandler.handleSendCommand(os: OutputStream, params: Map<String, String>) {
    val cmd = params["command"]
    val clientReleaseCode = params["release_code"]
    if (cmd.isNullOrEmpty()) {
        sendResponse(os, 400, "{\"status\":\"error\",\"message\":\"Missing 'command' parameter\"}", "application/json; charset=UTF-8")
        return
    }

    synchronized(LocalHttpServer::class.java) {
        val currentLockCode = LocalHttpServer.getActiveLockCode()
        if (currentLockCode != null && currentLockCode == clientReleaseCode) {
            LocalHttpServer.addPendingCommand(cmd)
            sendResponse(os, 200, "{\"status\":\"success\"}", "application/json; charset=UTF-8")
        } else {
            sendResponse(os, 200, "{\"status\":\"error\",\"message\":\"Not authorized / lock expired\"}", "application/json; charset=UTF-8")
        }
    }
}

internal fun ClientHandler.handlePollCommands(os: OutputStream) {
    val cmds = LocalHttpServer.getAndClearPendingCommands()
    val sb = StringBuilder()
    sb.append("{\"commands\":[")
    for (i in cmds.indices) {
        sb.append("\"").append(cmds[i].replace("\"", "\\\"")).append("\"")
        if (i < cmds.size - 1) {
            sb.append(",")
        }
    }
    sb.append("]}")
    sendResponse(os, 200, sb.toString(), "application/json; charset=UTF-8")
}
