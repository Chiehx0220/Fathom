package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliCdn
import io.github.aedev.flow.player.datasource.GoogleVideoRequestPolicy
import io.github.aedev.flow.player.error.StreamDenialClassifier
import okhttp3.Request
import okhttp3.Response
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
private const val GOOGLE_VIDEO = "googlevideo.com"

internal fun repCacheKey(
    serviceId: Int,
    mediaUrl: String,
    repId: String,
) = "${serviceId}_${mediaUrl}_rep_$repId"

internal fun fetchFromCdn(
    directUrl: String,
    cacheKey: String,
    rangeHeader: String?,
): Response {
    val request =
        Request
            .Builder()
            .url(directUrl)
            .header("User-Agent", cdnUserAgent(directUrl, cacheKey))
    if (GOOGLE_VIDEO in directUrl) {
        StreamDenialClassifier.clientOf(directUrl)?.let { client ->
            GoogleVideoRequestPolicy.headers(client).forEach { (name, value) -> request.header(name, value) }
        }
    }
    if (BilibiliCdn.isCdnUrl(directUrl) || "bilibili.com" in directUrl) {
        BilibiliCdn.headers().forEach { (name, value) -> request.header(name, value) }
    }
    if (rangeHeader != null) {
        request.header("Range", rangeHeader)
        serverLog("Forwarding Range to CDN: $rangeHeader")
    }
    return LocalServerCaches.httpClient.newCall(request.build()).execute()
}

/** GVS refusal, no replacement: web client → TV_TIZEN, video caches dropped; retry re-extracts. */
internal fun dropAfterDenial(
    directUrl: String,
    cacheKey: String,
    videoId: String?,
) {
    val client = StreamDenialClassifier.clientOf(directUrl)
    serverLog("CDN denial kind=${StreamDenialClassifier.classify(directUrl)} client=$client url=$directUrl")
    videoId?.let {
        if (client?.uppercase() in LocalServerYouTubeStreams.WEB_CLIENTS) {
            LocalServerYouTubeStreams.markWalled(it)
        } else {
            LocalServerYouTubeStreams.invalidate(it)
        }
    }
    LocalServerCaches.streamUrlCache.remove(cacheKey)
    LocalServerCaches.streamUaCache.remove(cacheKey)
}

/** User-Agent for [directUrl]: minting client, exact match. */
private fun cdnUserAgent(
    directUrl: String,
    cacheKey: String,
): String {
    if (GOOGLE_VIDEO !in directUrl) return DEFAULT_USER_AGENT
    return GoogleVideoRequestPolicy.userAgent(
        StreamDenialClassifier.clientOf(directUrl),
        cachedOrGuessedUserAgent(directUrl, cacheKey),
    )
}

private fun cachedOrGuessedUserAgent(
    directUrl: String,
    cacheKey: String,
): String {
    LocalServerCaches.streamUaCache.get(cacheKey)?.let { return it }
    // No cached InnerTube User-Agent (older entry or NewPipe fallback): guess from URL's client marker.
    return try {
        when {
            "c=IOS" in directUrl || "c=ios" in directUrl -> YoutubeParsingHelper.getIosUserAgent(null)
            "c=VISIONOS" in directUrl || "c=visionos" in directUrl -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            else -> YoutubeParsingHelper.getAndroidUserAgent(null)
        }
    } catch (e: Exception) {
        DEFAULT_USER_AGENT
    }
}
