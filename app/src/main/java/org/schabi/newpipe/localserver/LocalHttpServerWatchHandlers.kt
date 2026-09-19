package org.schabi.newpipe.localserver

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.localserver.LocalHttpServer.ClientHandler
import java.io.OutputStream

// Watch-page content, comments, and danmaku handlers - split from LocalHttpServer.kt, no behavior
// change. getCachedExtractor/commentsExtractorFor/bulletCommentJson need LocalHttpServer.
// qualification: companion members aren't in unqualified lexical scope across files.

internal fun ClientHandler.handleWatch(os: OutputStream, params: Map<String, String>, isTv: Boolean) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]

    // Immediately send the fast watch skeleton layout
    val html = HtmlRenderer.renderWatchSkeleton(serviceId, mediaUrl, isTv)
    sendResponse(os, 200, html, "text/html; charset=UTF-8")
}

// Shared extraction + history/engagement-state load for handleWatchContent/handleAudioWatch.
// Reuses getCachedExtractor() (shared with handleManifestProxy) instead of a redundant
// fetchPage(). Synchronized: extractor may be concurrently accessed by handleManifestProxy.
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
    // getThumbnailUrl() falls back to a stock-photo URL - only call once a real avatar is
    // confirmed, or the placeholder gets baked into the history row permanently.
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

// Fetched async post-load by renderWatchContent()/renderAudioWatch()'s inline <script>, not
// blocking the initial response. Returns an HTML fragment for innerHTML injection, not a full page.
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
            // getPage() only reads nextPage's continuation token, not fetchPage() state - safe on
            // a fresh extractor.
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

// Bilibili danmaku ("bullet comments"), fetched async post-load like handleComments(). Only
// Bilibili exposes a BulletCommentsExtractor (null elsewhere) - other services get an empty result.
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
        // Primes the cid cache BilibiliBulletCommentsExtractor reads (populated only by the stream
        // extractor's fetchPage()) - NPEs otherwise. Normally a cache hit via /watch-content.
        LocalHttpServer.getCachedExtractor(service, serviceId, mediaUrl)
        val extractor = service.getBulletCommentsExtractor(mediaUrl)
        if (extractor == null) {
            sendResponse(os, 200, "{\"danmaku\":[]}", "application/json")
            return
        }
        extractor.fetchPage()
        if (extractor.isLive) {
            // Live danmaku needs a persistent WebSocket - out of scope for this one-shot endpoint.
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
