package io.github.aedev.flow.localserver

import java.io.OutputStream
import java.util.UUID

// Toggle DB action endpoints (subscribe/block/bookmark/history/watch-later/like-dislike) + TV remote-control endpoints
// (send-link/release-lock/send-command/poll-commands). ClientHandler/dbHelper/sendResponse/getServiceId internal.

// Shared "share this video" field set/defaults for handleWatchLaterAction, handleRateVideoAction: video unseen by server, client-page metadata only.
private data class SharedVideoParams(
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val uploaderUrl: String?,
)

private fun parseSharedVideoParams(params: Map<String, String>): SharedVideoParams {
    val title = params["title"].takeUnless { it.isNullOrEmpty() } ?: "Shared Item"
    val uploader = params["uploader"] ?: ""
    val thumbnail = params["thumbnail"]
    val uploaderUrl = params["uploaderUrl"]
    return SharedVideoParams(title, uploader, thumbnail, uploaderUrl)
}

// Callers: web app fetch-based FT.api (app/js/api.js act()), success-only: all toggle handlers answer alike.
private fun ClientHandler.sendActionResult(os: OutputStream) {
    sendResponse(os, 200, "{\"status\":\"success\"}", "application/json")
}

internal fun ClientHandler.handleSubscribeAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val action = params["action"]
    val channelUrl = params["id"]

    if ("subscribe" == action && !channelUrl.isNullOrEmpty()) {
        val name = params["name"]
        val avatar = params["avatar"]
        dbHelper.nativeAddSubscription(channelUrl, name, avatar)
    } else if ("unsubscribe" == action && !channelUrl.isNullOrEmpty()) {
        dbHelper.nativeRemoveSubscription(channelUrl)
    }

    sendActionResult(os)
}

internal fun ClientHandler.handleBlockChannelAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val action = params["action"]
    val channelUrl = params["id"]

    if ("block" == action && !channelUrl.isNullOrEmpty()) {
        dbHelper.nativeBlockChannel(channelUrl)
    } else if ("unblock" == action && !channelUrl.isNullOrEmpty()) {
        dbHelper.nativeUnblockChannel(channelUrl)
    }

    sendActionResult(os)
}

internal fun ClientHandler.handlePlaylistBookmarkAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val action = params["action"]
    val playlistUrl = params["id"]

    if ("bookmark" == action && !playlistUrl.isNullOrEmpty()) {
        val name = params["name"]
        dbHelper.nativeBookmarkPlaylist(playlistUrl, name, null)
    } else if ("unbookmark" == action && !playlistUrl.isNullOrEmpty()) {
        dbHelper.nativeUnbookmarkPlaylist(playlistUrl)
    }

    sendActionResult(os)
}

internal fun ClientHandler.handleHistoryAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val action = params["action"]
    val url = params["url"]

    if ("remove" == action && !url.isNullOrEmpty()) {
        dbHelper.nativeRemoveFromHistory(url)
    } else if ("clear" == action) {
        dbHelper.nativeClearHistory()
    }

    sendActionResult(os)
}

internal fun ClientHandler.handleWatchLaterAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val action = params["action"]
    val url = params["url"]

    if ("add" == action && !url.isNullOrEmpty()) {
        val meta = parseSharedVideoParams(params)
        dbHelper.nativeAddWatchLater(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl)
    } else if ("remove" == action && !url.isNullOrEmpty()) {
        dbHelper.nativeRemoveWatchLater(url)
    }

    sendActionResult(os)
}

internal fun ClientHandler.handleRateVideoAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val action = params["action"]
    val url = params["url"]

    if (!url.isNullOrEmpty()) {
        val meta = parseSharedVideoParams(params)
        val serviceId = getServiceId(params)
        when (action) {
            "like" -> dbHelper.nativeLikeVideo(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl, serviceId)
            "dislike" -> dbHelper.nativeDislikeVideo(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl)
            "remove" -> dbHelper.nativeRemoveLikeState(url)
        }
    }

    sendActionResult(os)
}

internal fun ClientHandler.handleSendLink(
    os: OutputStream,
    params: Map<String, String>,
    clientIp: String?,
) {
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

    synchronized(RemoteSession) {
        var hasLock = false
        var currentLockCode = RemoteSession.getActiveLockCode()

        if (currentLockCode == null) {
            val newLockCode = UUID.randomUUID().toString()
            RemoteSession.tryLock(newLockCode, clientIp ?: "", videoTitle)
            currentLockCode = newLockCode
            hasLock = true
        } else if (currentLockCode == clientReleaseCode) {
            RemoteSession.tryLock(currentLockCode, clientIp ?: "", videoTitle)
            hasLock = true
        }

        if (hasLock) {
            // "__connect_only__": cast on non-video page (Home, Subscriptions): pairs remote control, no play_video command (would 404 as /watch).
            if ("__connect_only__" != videoUrl) {
                serverLog("Casting link: $videoUrl from client IP $clientIp")
                RemoteSession.addPendingCommand("play_video:$videoUrl")
            } else {
                serverLog("Remote connected (pairing only) from client IP $clientIp")
            }

            val json = "{\"status\":\"success\",\"release_code\":\"" + currentLockCode + "\"}"
            sendResponse(os, 200, json, "application/json; charset=UTF-8")
        } else {
            var busyMsg = "Server is currently controlled by device at IP " + RemoteSession.getActiveClientIp()
            if (RemoteSession.getActiveVideoTitle() != null) {
                busyMsg += " playing: " + RemoteSession.getActiveVideoTitle()
            }
            val json = "{\"status\":\"busy\",\"message\":\"" + busyMsg.replace("\"", "\\\"") + "\"}"
            sendResponse(os, 200, json, "application/json; charset=UTF-8")
        }
    }
}

internal fun ClientHandler.handleReleaseLock(
    os: OutputStream,
    params: Map<String, String>,
) {
    val clientReleaseCode = params["release_code"]
    if (clientReleaseCode.isNullOrEmpty()) {
        sendResponse(os, 400, "{\"status\":\"error\",\"message\":\"Missing 'release_code' parameter\"}", "application/json; charset=UTF-8")
        return
    }

    synchronized(RemoteSession) {
        val currentLockCode = RemoteSession.getActiveLockCode()
        if (currentLockCode != null && currentLockCode == clientReleaseCode) {
            RemoteSession.releaseLock()
            serverLog("Lock released by client.")
            sendResponse(os, 200, "{\"status\":\"success\"}", "application/json; charset=UTF-8")
        } else {
            sendResponse(os, 200, "{\"status\":\"error\",\"message\":\"Invalid or expired lock code\"}", "application/json; charset=UTF-8")
        }
    }
}

internal fun ClientHandler.handleSendCommand(
    os: OutputStream,
    params: Map<String, String>,
) {
    val cmd = params["command"]
    val clientReleaseCode = params["release_code"]
    if (cmd.isNullOrEmpty()) {
        sendResponse(os, 400, "{\"status\":\"error\",\"message\":\"Missing 'command' parameter\"}", "application/json; charset=UTF-8")
        return
    }

    synchronized(RemoteSession) {
        val currentLockCode = RemoteSession.getActiveLockCode()
        if (currentLockCode != null && currentLockCode == clientReleaseCode) {
            RemoteSession.addPendingCommand(cmd)
            sendResponse(os, 200, "{\"status\":\"success\"}", "application/json; charset=UTF-8")
        } else {
            sendResponse(os, 200, "{\"status\":\"error\",\"message\":\"Not authorized / lock expired\"}", "application/json; charset=UTF-8")
        }
    }
}

// Chapter titles as JSON array (arbitrary text: no safe delimiter).
private fun parseChapterTitles(raw: String?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    return try {
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).map { arr.optString(it, "") }
    } catch (e: Exception) {
        emptyList()
    }
}

// Paired page reports playback ~1/s: phone remote progress + mode.
internal fun ClientHandler.handleRemoteState(
    os: OutputStream,
    params: Map<String, String>,
) {
    val code = RemoteSession.getActiveLockCode()
    if (code == null || code != params["release_code"]) {
        sendResponse(os, 200, "{\"status\":\"error\"}", "application/json; charset=UTF-8")
        return
    }
    val watching = params["watching"] == "1"
    val previous = RemoteSession.remoteState.value
    RemoteSession.updateRemoteState(
        RemoteState(
            watching = watching,
            minimized = params["mini"] == "1",
            title = params["title"]?.takeIf { it.isNotEmpty() },
            positionSec = params["t"]?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0,
            durationSec = params["d"]?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0,
            paused = params["paused"] != "0",
            volume = params["vol"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f,
            muted = params["muted"] == "1",
            fullscreen = params["fs"] == "1",
            chapters = parseChapterTitles(params["chapters"]),
            chapterIndex = params["chapi"]?.toIntOrNull() ?: -1,
            skipLabel = params["skip"]?.takeIf { it.isNotEmpty() },
            services =
                params["svcs"].orEmpty().split(',').mapNotNull { entry ->
                    val colon = entry.indexOf(':')
                    if (colon <= 0) null else entry.substring(0, colon).toIntOrNull()?.let { it to entry.substring(colon + 1) }
                },
            activeService = params["svc"]?.toIntOrNull(),
            qualities = parseQualities(params["qualities"]),
            quality = params["quality"]?.takeIf { it.isNotEmpty() },
            chosenQuality = RemoteSession.choiceHold.chosenQuality(params["qchosen"], previous.chosenQuality),
            rate = RemoteSession.choiceHold.rate(params["rate"], previous.rate),
            danmaku = RemoteSession.choiceHold.danmaku(params["danmaku"], previous.danmaku),
            rating = RemoteSession.choiceHold.rating(params["like"], previous.rating),
        ),
    )
    sendResponse(os, 200, "{\"status\":\"success\"}", "application/json; charset=UTF-8")
}

internal fun ClientHandler.handlePollCommands(os: OutputStream) {
    val cmds = RemoteSession.getAndClearPendingCommands()
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
