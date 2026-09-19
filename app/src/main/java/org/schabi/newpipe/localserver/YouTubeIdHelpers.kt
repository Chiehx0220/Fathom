package org.schabi.newpipe.localserver

import io.github.aedev.flow.data.model.isYouTubeServiceId
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList

/**
 * Converts between Local Server's URL-keyed [InfoItem][org.schabi.newpipe.extractor.InfoItem]s
 * and native storage's bare-ID keys.
 */

/**
 * Bare channel ID -> URL. YouTube: canonical form. Other [serviceId]: via the service's link
 * handler, falling back to the YouTube form on failure.
 */
fun channelIdToUrl(channelId: String, serviceId: Int = ServiceList.YouTube.serviceId): String {
    val youtubeForm = "https://www.youtube.com/channel/$channelId"
    if (serviceId.isYouTubeServiceId) return youtubeForm
    return runCatching { NewPipe.getService(serviceId).channelLHFactory.getUrl(channelId) }
        .getOrDefault(youtubeForm)
}

/**
 * Channel URL -> bare ID. Manually parses `/channel/UC...`, `/@handle`, `/c/name`, `/user/name`
 * first - [NewPipe.getServiceByUrl] returns a type-prefixed id (`"channel/UC..."`) for YouTube,
 * incompatible with this module's bare-id convention. Falls back to it only for non-YouTube URLs
 * (e.g. a Bilibili space page).
 */
fun channelUrlToId(url: String?): String? {
    if (url == null) return null
    var normalized = url.trim()
    if (normalized.contains("m.youtube.com")) {
        normalized = normalized.replace("m.youtube.com", "www.youtube.com")
    }
    if (normalized.endsWith("/")) normalized = normalized.dropLast(1)
    for (marker in listOf("/channel/", "/@", "/c/", "/user/")) {
        val idx = normalized.indexOf(marker)
        if (idx != -1) {
            val id = normalized.substring(idx + marker.length).substringBefore("?")
            if (id.isNotEmpty()) return if (marker == "/@") "@$id" else id
        }
    }
    return runCatching { NewPipe.getServiceByUrl(url).channelLHFactory.getId(url) }.getOrNull()
}

/**
 * Bare video ID -> URL. YouTube: canonical form. Other [serviceId]: via the service's link
 * handler - must round-trip through its own extractor, since rendering keys everything off
 * `item.url`.
 */
fun videoIdToUrl(videoId: String, serviceId: Int = ServiceList.YouTube.serviceId): String {
    val youtubeForm = "https://www.youtube.com/watch?v=$videoId"
    if (serviceId.isYouTubeServiceId) return youtubeForm
    return runCatching { NewPipe.getService(serviceId).streamLHFactory.getUrl(videoId) }
        .getOrDefault(youtubeForm)
}

fun playlistIdToUrl(playlistId: String): String = "https://www.youtube.com/playlist?list=$playlistId"

/** Bare `list=` id from a playlist URL - matches NewPipeExtractor's own playlist id for it. */
fun playlistUrlToId(url: String?): String? {
    if (url == null) return null
    val idx = url.indexOf("list=")
    if (idx == -1) return null
    return url.substring(idx + 5).substringBefore("&").takeIf { it.isNotEmpty() }
}
