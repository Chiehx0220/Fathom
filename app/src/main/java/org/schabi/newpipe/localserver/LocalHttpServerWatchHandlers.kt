package org.schabi.newpipe.localserver

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.localserver.LocalHttpServer.ClientHandler
import java.io.OutputStream

// Video/audio watch-page content, comments, and danmaku handlers, split out of LocalHttpServer.kt
// as extension functions on ClientHandler purely to keep that file from growing without bound -
// no behavior changed by the move. getCachedExtractor/commentsExtractorFor/bulletCommentJson are
// LocalHttpServer's companion object functions, qualified here because they're outside that
// class's own lexical scope in this file, not because they're private - none of them are.

internal fun ClientHandler.handleWatch(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]

    // Immediately send the fast watch skeleton layout
    val html = HtmlRenderer.renderWatchSkeleton(serviceId, mediaUrl, isTv)
    sendResponse(os, 200, html, "text/html; charset=UTF-8")
}

// The extraction + history/engagement-state loading that handleWatchContent() and
// handleAudioWatch() both need before they can render anything - identical in both until the
// point each picks its own render function. Shares one page extraction with handleManifestProxy
// for this same video (getCachedExtractor), instead of each doing its own independent
// fetchPage(). Synchronized since extractor may be concurrently shared with a handleManifestProxy
// request for the same video, and StreamInfo.getInfo() calls many extractor getters in bulk here.
private data class WatchPageInfo(
    val info: StreamInfo,
    val isSubscribed: Boolean,
    val isWatchLater: Boolean,
    val likeState: String?,
)

private fun ClientHandler.loadWatchPageInfo(service: StreamingService, serviceId: Int, mediaUrl: String): WatchPageInfo {
    val extractor = LocalHttpServer.getCachedExtractor(service, serviceId, mediaUrl)
    val info: StreamInfo
    synchronized(extractor) {
        info = StreamInfo.getInfo(extractor)
    }
    info.relatedItems = dbHelper.nativeRelatedVideos(info, serviceId)

    var thumbUrl = ""
    if (info.thumbnails != null && !info.thumbnails.isEmpty()) {
        thumbUrl = info.thumbnails[info.thumbnails.size - 1].url
    }
    // getThumbnailUrl() always returns a non-null stock-photo URL as its own fallback, so it's
    // only safe to call once we already know a real avatar exists - otherwise that placeholder
    // would get baked permanently into the history row.
    var uploaderAvatarUrl: String? = null
    if (info.uploaderAvatars != null && !info.uploaderAvatars.isEmpty()) {
        uploaderAvatarUrl = HtmlRenderer.getThumbnailUrl(info.uploaderAvatars)
    }
    dbHelper.nativeSaveToHistory(info.name, info.url, info.uploaderName, thumbUrl, serviceId, info.uploaderUrl, uploaderAvatarUrl)
    reportFlowNeuroClick(info, serviceId)

    return WatchPageInfo(
        info = info,
        isSubscribed = dbHelper.nativeIsSubscribed(info.uploaderUrl),
        isWatchLater = dbHelper.nativeIsWatchLater(info.url),
        likeState = dbHelper.nativeLikeState(info.url),
    )
}

@Throws(Exception::class)
internal fun ClientHandler.handleWatchContent(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]!!

    try {
        val service = NewPipe.getService(serviceId)
        val page = loadWatchPageInfo(service, serviceId, mediaUrl)
        val targetQuality = dbHelper.nativeVideoQuality()
        val html = HtmlRenderer.renderWatchContent(serviceId, page.info, page.isSubscribed, page.isWatchLater, page.likeState, isTv, targetQuality, page.info.duration)
        sendResponse(os, 200, html, "text/html; charset=UTF-8")
    } catch (e: Exception) {
        sendResponse(os, 500, "Error: " + e.message, "text/plain; charset=UTF-8")
    }
}

// Fetched by an inline <script> in renderWatchContent()/renderAudioWatch() after the video itself
// has loaded, rather than blocking the initial /watch-content response on it - comments can be a
// slow network round-trip and shouldn't delay playback start. Returns a bare HTML fragment (like
// the "ajax" subscriptions-feed branch), not a full page, since it's injected via innerHTML into
// an already-rendered page.
@Throws(Exception::class)
internal fun ClientHandler.handleComments(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
    val serviceId = getServiceId(params)
    val videoUrl = params["id"]
    if (videoUrl.isNullOrEmpty()) {
        sendResponse(os, 200, "<div class=\"loading-placeholder\">No video specified.</div>", "text/html; charset=UTF-8")
        return
    }

    val nextPage = HtmlRenderer.deserializePage(params["nextPage"])

    try {
        val service = NewPipe.getService(serviceId)
        val extractor = LocalHttpServer.commentsExtractorFor(service, videoUrl)

        val page = if (nextPage != null) {
            // YoutubeCommentsExtractor.getPage() only reads the continuation token already inside
            // nextPage - it doesn't touch anything fetchPage() would have populated, so it's safe
            // to skip for a fresh extractor with no prior call to rely on.
            extractor.getPage(nextPage)
        } else {
            extractor.fetchPage()
            if (extractor.isCommentsDisabled) {
                sendResponse(os, 200, "<div class=\"loading-placeholder\">Comments are disabled for this video.</div>", "text/html; charset=UTF-8")
                return
            }
            extractor.initialPage
        }

        val isReplies = params["context"] == "replies"
        val html = HtmlRenderer.renderComments(serviceId, videoUrl, page.items, page.nextPage, isTv, isReplies)
        sendResponse(os, 200, html, "text/html; charset=UTF-8")
    } catch (e: Exception) {
        sendResponse(os, 200, "<div class=\"loading-placeholder\">Failed to load comments: ${e.message}</div>", "text/html; charset=UTF-8")
    }
}

// Bilibili danmaku ("bullet comments"). Fetched by an inline <script> in renderWatchContent()
// after the video itself has loaded, same rationale as handleComments() above - a video can carry
// thousands of these, so it shouldn't block the initial page. Only Bilibili currently exposes a
// BulletCommentsExtractor (StreamingService.getBulletCommentsExtractor() returns null otherwise),
// so this comes back empty for YouTube rather than erroring.
@Throws(Exception::class)
internal fun ClientHandler.handleDanmaku(os: OutputStream, params: Map<String, String>) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]
    if (mediaUrl.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
        return
    }
    try {
        val service = NewPipe.getService(serviceId)
        // BilibiliBulletCommentsExtractor reads the video's cid out of a cache that only the
        // stream extractor's own fetchPage() populates (keyed by video id) - without this,
        // looking it up NPEs. In practice /watch-content already primed this cache for the video
        // the client is currently watching, so this is normally a cache hit.
        LocalHttpServer.getCachedExtractor(service, serviceId, mediaUrl)
        val extractor = service.getBulletCommentsExtractor(mediaUrl)
        if (extractor == null) {
            sendResponse(os, 200, "{\"danmaku\":[]}", "application/json")
            return
        }
        extractor.fetchPage()
        if (extractor.isLive) {
            // Live danmaku needs a persistent connection (WebSocket) this one-shot HTTP endpoint
            // has no equivalent of - out of scope for now.
            extractor.disconnect()
            sendResponse(os, 200, "{\"danmaku\":[]}", "application/json")
            return
        }
        val danmaku = org.json.JSONArray()
        for (item in extractor.initialPage.items) {
            danmaku.put(LocalHttpServer.bulletCommentJson(item))
        }
        val json = org.json.JSONObject()
        json.put("danmaku", danmaku)
        sendResponse(os, 200, json.toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

@Throws(Exception::class)
internal fun ClientHandler.handleAudioWatch(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]
    if (mediaUrl.isNullOrEmpty()) {
        sendRedirect(os, "/?serviceId=$serviceId")
        return
    }

    try {
        val service = NewPipe.getService(serviceId)
        val page = loadWatchPageInfo(service, serviceId, mediaUrl)
        val html = HtmlRenderer.renderAudioWatch(serviceId, page.info, page.isSubscribed, page.isWatchLater, page.likeState, isTv)
        sendResponse(os, 200, html, "text/html; charset=UTF-8")
    } catch (e: Exception) {
        sendResponse(os, 500, "Error loading audio stream: " + e.message, "text/plain; charset=UTF-8")
    }
}
