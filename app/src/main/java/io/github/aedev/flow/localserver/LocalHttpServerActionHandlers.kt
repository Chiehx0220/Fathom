package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliLink
import io.github.aedev.flow.utils.YouTubeLink
import io.github.aedev.flow.utils.parseYouTubeLink
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
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

private fun parseSharedVideoParams(params: Query): SharedVideoParams {
    val title = params["title"].takeUnless { it.isNullOrEmpty() } ?: "Shared Item"
    val uploader = params["uploader"] ?: ""
    val thumbnail = params["thumbnail"]
    val uploaderUrl = params["uploaderUrl"]
    return SharedVideoParams(title, uploader, thumbnail, uploaderUrl)
}

// Toggle handlers answer alike (api.js act()).
private fun RequestContext.sendActionResult(reply: Reply) {
    reply.sendJson(SUCCESS)
}

private fun unknownAction(): Nothing = throw BadRequestException("Unknown action")

internal fun RequestContext.handleSubscribeAction(
    reply: Reply,
    params: Query,
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
    params: Query,
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
    params: Query,
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
    params: Query,
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
    params: Query,
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
    params: Query,
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
    params: Query,
    clientIp: String?,
) {
    val videoUrl = params.required("id")
    val clientReleaseCode = params["release_code"]
    val takeover = params.flag("takeover")
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
            reply.sendJson(Status("success", releaseCode = currentLockCode))
        } else {
            val title = RemoteSession.getActiveVideoTitle()
            val busyMsg =
                "Server is currently controlled by device at IP ${RemoteSession.getActiveClientIp()}" +
                    (title?.let { " playing: $it" } ?: "")
            reply.sendJson(Status("busy", message = busyMsg))
        }
    }
}

internal fun RequestContext.handleReleaseLock(
    reply: Reply,
    params: Query,
) {
    val clientReleaseCode = params.required("release_code")
    synchronized(RemoteSession) {
        if (RemoteSession.getActiveLockCode() == clientReleaseCode) {
            RemoteSession.releaseLock()
            serverLog("Lock released by client.")
            reply.sendJson(SUCCESS)
        } else {
            reply.sendJson(Status("error", message = "Invalid or expired lock code"))
        }
    }
}

// Chapter titles as JSON array (arbitrary text: no safe delimiter).
private fun parseChapterTitles(raw: String?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    return try {
        Json.parseToJsonElement(raw).jsonArray.map { (it as? JsonPrimitive)?.content.orEmpty() }
    } catch (e: Exception) {
        emptyList()
    }
}

// Paired page reports playback ~1/s: phone remote progress + mode.
internal fun RequestContext.handleRemoteState(
    reply: Reply,
    params: Query,
) {
    val code = RemoteSession.getActiveLockCode()
    if (code == null || code != params["release_code"]) {
        reply.sendJson(Status("error"))
        return
    }
    RemoteSession.markSeen()
    val watching = params.flag("watching")
    val previous = RemoteSession.remoteState.value
    RemoteSession.updateRemoteState(
        RemoteState(
            watching = watching,
            minimized = params.flag("mini"),
            title = params["title"]?.takeIf { it.isNotEmpty() },
            positionSec = params.double("t")?.takeIf { it.isFinite() } ?: 0.0,
            durationSec = params.double("d")?.takeIf { it.isFinite() } ?: 0.0,
            paused = params["paused"] != "0",
            volume = params.float("vol")?.coerceIn(0f, 1f) ?: 1f,
            muted = params.flag("muted"),
            fullscreen = params.flag("fs"),
            chapters = parseChapterTitles(params["chapters"]),
            chapterIndex = params.int("chapi") ?: -1,
            skipLabel = params["skip"]?.takeIf { it.isNotEmpty() },
            services =
                params["svcs"].orEmpty().split(',').mapNotNull { entry ->
                    val colon = entry.indexOf(':')
                    if (colon <= 0) null else entry.substring(0, colon).toIntOrNull()?.let { it to entry.substring(colon + 1) }
                },
            activeService = params.int("svc"),
            qualities = parseQualities(params["qualities"]),
            quality = params["quality"]?.takeIf { it.isNotEmpty() },
            chosenQuality = RemoteSession.choiceHold.chosenQuality(params["qchosen"], previous.chosenQuality),
            rate = RemoteSession.choiceHold.rate(params["rate"], previous.rate),
            danmaku = RemoteSession.choiceHold.danmaku(params["danmaku"], previous.danmaku),
            rating = RemoteSession.choiceHold.rating(params["like"], previous.rating),
        ),
    )
    reply.sendJson(SUCCESS)
}

private const val HANDOFF_TITLE_MAX = 200

/** `/handoff`: saves the position to history, then emits a [Handoff] for the app. */
internal fun RequestContext.handleHandoff(
    reply: Reply,
    params: Query,
) {
    val url = params["id"].orEmpty().trim()
    val link = parseYouTubeLink(url)
    val playable = link is YouTubeLink.Video || link is YouTubeLink.Short || BilibiliLink.isBilibili(url)
    if (!playable) throw BadRequestException("Unsupported link")
    val position = params.double("t")?.takeIf { it.isFinite() && it >= 0 }
    val duration = params.double("d")?.takeIf { it.isFinite() && it > 0 }
    val positionMs = position?.let { (it * 1000).toLong() }
    if (positionMs != null && duration != null) dbHelper.updateWatchPosition(url, positionMs, (duration * 1000).toLong())
    RemoteSession.handOff(Handoff(url, params["title"].orEmpty().take(HANDOFF_TITLE_MAX), LocalServerMedia.getVideoId(url), positionMs))
    sendActionResult(reply)
}

@Throws(Exception::class)
internal fun RequestContext.handleSearchHistory(
    reply: Reply,
    params: Query,
) {
    val deleteQuery = params["delete"]
    if (!deleteQuery.isNullOrEmpty()) {
        dbHelper.deleteSearchQuery(deleteQuery)
        reply.sendJson(SUCCESS)
        return
    }
    reply.sendJson(dbHelper.searchHistory())
}
