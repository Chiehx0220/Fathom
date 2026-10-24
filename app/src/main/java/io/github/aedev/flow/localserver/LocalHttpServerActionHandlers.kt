package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliLink
import io.github.aedev.flow.utils.YouTubeLink
import io.github.aedev.flow.utils.parseYouTubeLink
import org.json.JSONObject
import java.io.OutputStream
import java.util.UUID

// Toggle DB action endpoints (subscribe/block/bookmark/history/watch-later/like-dislike) + TV remote-control endpoints
// (send-link/release-lock/remote-state). ClientHandler/dbHelper/sendResponse/getServiceId internal.

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
    sendJson(os, "{\"status\":\"success\"}")
}

private fun unknownAction(): Nothing = throw BadRequestException("Unknown action")

internal fun ClientHandler.handleSubscribeAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val channelUrl = params.required("id")
    when (params["action"]) {
        "subscribe" -> dbHelper.nativeAddSubscription(channelUrl, params["name"], params["avatar"])
        "unsubscribe" -> dbHelper.nativeRemoveSubscription(channelUrl)
        else -> unknownAction()
    }
    sendActionResult(os)
}

internal fun ClientHandler.handleBlockChannelAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val channelUrl = params.required("id")
    when (params["action"]) {
        "block" -> dbHelper.nativeBlockChannel(channelUrl)
        "unblock" -> dbHelper.nativeUnblockChannel(channelUrl)
        else -> unknownAction()
    }
    sendActionResult(os)
}

internal fun ClientHandler.handlePlaylistBookmarkAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val playlistUrl = params.required("id")
    when (params["action"]) {
        "bookmark" -> dbHelper.nativeBookmarkPlaylist(playlistUrl, params["name"], null)
        "unbookmark" -> dbHelper.nativeUnbookmarkPlaylist(playlistUrl)
        else -> unknownAction()
    }
    sendActionResult(os)
}

internal fun ClientHandler.handleHistoryAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    when (params["action"]) {
        "remove" -> dbHelper.nativeRemoveFromHistory(params.required("url"))
        "clear" -> dbHelper.nativeClearHistory()
        else -> unknownAction()
    }
    sendActionResult(os)
}

internal fun ClientHandler.handleWatchLaterAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val url = params.required("url")
    when (params["action"]) {
        "add" -> {
            val meta = parseSharedVideoParams(params)
            dbHelper.nativeAddWatchLater(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl)
        }

        "remove" -> {
            dbHelper.nativeRemoveWatchLater(url)
        }

        else -> {
            unknownAction()
        }
    }
    sendActionResult(os)
}

internal fun ClientHandler.handleRateVideoAction(
    os: OutputStream,
    params: Map<String, String>,
) {
    val url = params.required("url")
    val meta = parseSharedVideoParams(params)
    when (params["action"]) {
        "like" -> dbHelper.nativeLikeVideo(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl, getServiceId(params))
        "dislike" -> dbHelper.nativeDislikeVideo(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl)
        "remove" -> dbHelper.nativeRemoveLikeState(url)
        else -> unknownAction()
    }
    sendActionResult(os)
}

internal fun ClientHandler.handleSendLink(
    os: OutputStream,
    params: Map<String, String>,
    clientIp: String?,
) {
    val videoUrl = params.required("id")
    val clientReleaseCode = params["release_code"]
    val takeover = params["takeover"] == "1"
    val videoTitle = params["title"].takeUnless { it.isNullOrEmpty() } ?: "Video"

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
        } else if (takeover) {
            val newLockCode = UUID.randomUUID().toString()
            RemoteSession.takeOver(newLockCode, clientIp ?: "", videoTitle)
            currentLockCode = newLockCode
            hasLock = true
            serverLog("Remote taken over by client IP $clientIp")
        }

        if (hasLock) {
            // "__connect_only__": cast on non-video page (Home, Subscriptions): pairs remote control, no play_video command (would 404 as /watch).
            if ("__connect_only__" != videoUrl) {
                serverLog("Casting link: $videoUrl from client IP $clientIp")
                RemoteSession.broadcast("play_video:$videoUrl")
            } else {
                serverLog("Remote connected (pairing only) from client IP $clientIp")
            }
            sendJson(os, JSONObject().put("status", "success").put("release_code", currentLockCode))
        } else {
            val title = RemoteSession.getActiveVideoTitle()
            val busyMsg =
                "Server is currently controlled by device at IP ${RemoteSession.getActiveClientIp()}" +
                    (title?.let { " playing: $it" } ?: "")
            sendJson(os, JSONObject().put("status", "busy").put("message", busyMsg))
        }
    }
}

internal fun ClientHandler.handleReleaseLock(
    os: OutputStream,
    params: Map<String, String>,
) {
    val clientReleaseCode = params.required("release_code")
    synchronized(RemoteSession) {
        if (RemoteSession.getActiveLockCode() == clientReleaseCode) {
            RemoteSession.releaseLock()
            serverLog("Lock released by client.")
            sendJson(os, "{\"status\":\"success\"}")
        } else {
            sendJson(os, "{\"status\":\"error\",\"message\":\"Invalid or expired lock code\"}")
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
        sendJson(os, "{\"status\":\"error\"}")
        return
    }
    RemoteSession.markSeen()
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
    sendJson(os, "{\"status\":\"success\"}")
}

private const val HANDOFF_TITLE_MAX = 200

/** `/handoff`: saves the position to history, then emits a [Handoff] for the app. */
internal fun ClientHandler.handleHandoff(
    os: OutputStream,
    params: Map<String, String>,
) {
    val url = params["id"].orEmpty().trim()
    val link = parseYouTubeLink(url)
    val playable = link is YouTubeLink.Video || link is YouTubeLink.Short || BilibiliLink.isBilibili(url)
    if (!playable) throw BadRequestException("Unsupported link")
    val position = params["t"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val duration = params["d"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val positionMs = position?.let { (it * 1000).toLong() }
    if (positionMs != null && duration != null) dbHelper.nativeUpdateWatchPosition(url, positionMs, (duration * 1000).toLong())
    RemoteSession.handOff(Handoff(url, params["title"].orEmpty().take(HANDOFF_TITLE_MAX), LocalServerMedia.getVideoId(url), positionMs))
    sendActionResult(os)
}
