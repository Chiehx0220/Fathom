package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliLink
import io.github.aedev.flow.utils.YouTubeLink
import io.github.aedev.flow.utils.parseYouTubeLink
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.util.UUID

// Toggle actions (subscribe/block/bookmark/history/watch-later/rate) + remote control (send-link/release-lock/remote-state).

// Client-supplied video metadata (server has not seen the video).
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

// Toggle handlers answer alike (api.js act()).
private fun RequestContext.sendActionResult(reply: Reply) {
    reply.sendJson("{\"status\":\"success\"}")
}

private fun unknownAction(): Nothing = throw BadRequestException("Unknown action")

internal fun RequestContext.handleSubscribeAction(
    reply: Reply,
    params: Map<String, String>,
) {
    val channelUrl = params.required("id")
    when (params["action"]) {
        "subscribe" -> dbHelper.addSubscription(channelUrl, params["name"], params["avatar"])
        "unsubscribe" -> dbHelper.removeSubscription(channelUrl)
        else -> unknownAction()
    }
    sendActionResult(reply)
}

internal fun RequestContext.handleBlockChannelAction(
    reply: Reply,
    params: Map<String, String>,
) {
    val channelUrl = params.required("id")
    when (params["action"]) {
        "block" -> dbHelper.blockChannel(channelUrl)
        "unblock" -> dbHelper.unblockChannel(channelUrl)
        else -> unknownAction()
    }
    sendActionResult(reply)
}

internal fun RequestContext.handlePlaylistBookmarkAction(
    reply: Reply,
    params: Map<String, String>,
) {
    val playlistUrl = params.required("id")
    when (params["action"]) {
        "bookmark" -> dbHelper.bookmarkPlaylist(playlistUrl, params["name"], null)
        "unbookmark" -> dbHelper.unbookmarkPlaylist(playlistUrl)
        else -> unknownAction()
    }
    sendActionResult(reply)
}

internal fun RequestContext.handleHistoryAction(
    reply: Reply,
    params: Map<String, String>,
) {
    when (params["action"]) {
        "remove" -> dbHelper.removeFromHistory(params.required("url"))
        "clear" -> dbHelper.clearHistory()
        else -> unknownAction()
    }
    sendActionResult(reply)
}

internal fun RequestContext.handleWatchLaterAction(
    reply: Reply,
    params: Map<String, String>,
) {
    val url = params.required("url")
    when (params["action"]) {
        "add" -> {
            val meta = parseSharedVideoParams(params)
            dbHelper.addWatchLater(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl)
        }

        "remove" -> {
            dbHelper.removeWatchLater(url)
        }

        else -> {
            unknownAction()
        }
    }
    sendActionResult(reply)
}

internal fun RequestContext.handleRateVideoAction(
    reply: Reply,
    params: Map<String, String>,
) {
    val url = params.required("url")
    val meta = parseSharedVideoParams(params)
    when (params["action"]) {
        "like" -> dbHelper.likeVideo(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl, getServiceId(params))
        "dislike" -> dbHelper.dislikeVideo(url, meta.title, meta.uploader, meta.thumbnail, meta.uploaderUrl)
        "remove" -> dbHelper.removeLikeState(url)
        else -> unknownAction()
    }
    sendActionResult(reply)
}

internal fun RequestContext.handleSendLink(
    reply: Reply,
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
            // __connect_only__: pair without play_video (non-video page; /watch would 404).
            if ("__connect_only__" != videoUrl) {
                serverLog("Casting link: $videoUrl from client IP $clientIp")
                RemoteSession.broadcast("play_video:$videoUrl")
            } else {
                serverLog("Remote connected (pairing only) from client IP $clientIp")
            }
            reply.sendJson(JSONObject().put("status", "success").put("release_code", currentLockCode))
        } else {
            val title = RemoteSession.getActiveVideoTitle()
            val busyMsg =
                "Server is currently controlled by device at IP ${RemoteSession.getActiveClientIp()}" +
                    (title?.let { " playing: $it" } ?: "")
            reply.sendJson(JSONObject().put("status", "busy").put("message", busyMsg))
        }
    }
}

internal fun RequestContext.handleReleaseLock(
    reply: Reply,
    params: Map<String, String>,
) {
    val clientReleaseCode = params.required("release_code")
    synchronized(RemoteSession) {
        if (RemoteSession.getActiveLockCode() == clientReleaseCode) {
            RemoteSession.releaseLock()
            serverLog("Lock released by client.")
            reply.sendJson("{\"status\":\"success\"}")
        } else {
            reply.sendJson("{\"status\":\"error\",\"message\":\"Invalid or expired lock code\"}")
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
internal fun RequestContext.handleRemoteState(
    reply: Reply,
    params: Map<String, String>,
) {
    val code = RemoteSession.getActiveLockCode()
    if (code == null || code != params["release_code"]) {
        reply.sendJson("{\"status\":\"error\"}")
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
    reply.sendJson("{\"status\":\"success\"}")
}

private const val HANDOFF_TITLE_MAX = 200

/** `/handoff`: saves the position to history, then emits a [Handoff] for the app. */
internal fun RequestContext.handleHandoff(
    reply: Reply,
    params: Map<String, String>,
) {
    val url = params["id"].orEmpty().trim()
    val link = parseYouTubeLink(url)
    val playable = link is YouTubeLink.Video || link is YouTubeLink.Short || BilibiliLink.isBilibili(url)
    if (!playable) throw BadRequestException("Unsupported link")
    val position = params["t"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val duration = params["d"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val positionMs = position?.let { (it * 1000).toLong() }
    if (positionMs != null && duration != null) dbHelper.updateWatchPosition(url, positionMs, (duration * 1000).toLong())
    RemoteSession.handOff(Handoff(url, params["title"].orEmpty().take(HANDOFF_TITLE_MAX), LocalServerMedia.getVideoId(url), positionMs))
    sendActionResult(reply)
}

@Throws(Exception::class)
internal fun RequestContext.handleSearchHistory(
    reply: Reply,
    params: Map<String, String>,
) {
    val deleteQuery = params["delete"]
    if (!deleteQuery.isNullOrEmpty()) {
        dbHelper.deleteSearchQuery(deleteQuery)
        reply.sendJson("{\"status\":\"success\"}")
        return
    }
    reply.sendJson(JSONArray(dbHelper.searchHistory()).toString())
}
