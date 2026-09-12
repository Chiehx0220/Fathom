package org.schabi.newpipe.localserver

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList

/**
 * Local Server keys videos/channels by their full YouTube URL; Flow's native subscription and
 * watch-history storage keys them by bare ID. These convert between the two at the boundary
 * where Local Server reads/writes Flow's native data.
 */

/**
 * Builds a channel URL from a bare channel ID. For YouTube (the default) this is just the
 * canonical form; for any other [serviceId] it resolves through that service's own link handler,
 * falling back to the plain YouTube form if resolution fails.
 */
fun channelIdToUrl(channelId: String, serviceId: Int = ServiceList.YouTube.serviceId): String {
    val youtubeForm = "https://www.youtube.com/channel/$channelId"
    if (serviceId == ServiceList.YouTube.serviceId) return youtubeForm
    return runCatching { NewPipe.getService(serviceId).channelLHFactory.getUrl(channelId) }
        .getOrDefault(youtubeForm)
}

/**
 * Extracts a bare channel ID from a channel URL. Tries manual parsing of the canonical
 * `/channel/UC...` form (what NewPipeExtractor's YouTube uploader/channel URLs resolve to in the
 * vast majority of cases this module encounters them) as well as `/@handle`, `/c/name`,
 * `/user/name` FIRST - YouTube's own [NewPipe.getServiceByUrl] factory returns the id prefixed
 * with its type (`"channel/UC..."`, not bare `"UC..."`), which doesn't match the bare-id format
 * every other caller in this module (channelIdToUrl, subscription/history storage) assumes, so it
 * must not be used for YouTube. Falls back to [NewPipe.getServiceByUrl] only when none of those
 * markers match, which is how a non-YouTube URL (e.g. a Bilibili space page) resolves through its
 * own extractor instead of being forced through YouTube's URL shape.
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
 * Builds a video URL from a bare video ID. For YouTube (the default) this is just the canonical
 * form; for any other [serviceId] (e.g. Bilibili) it resolves the URL through that service's own
 * link handler, falling back to the plain YouTube form if resolution fails. Local Server's own
 * [InfoItem][org.schabi.newpipe.extractor.InfoItem] rendering keys everything off `item.url`, so a
 * non-YouTube video whose URL doesn't round-trip back through its own extractor silently breaks
 * resume/watched-matching/re-extraction for it.
 */
fun videoIdToUrl(videoId: String, serviceId: Int = ServiceList.YouTube.serviceId): String {
    val youtubeForm = "https://www.youtube.com/watch?v=$videoId"
    if (serviceId == ServiceList.YouTube.serviceId) return youtubeForm
    return runCatching { NewPipe.getService(serviceId).streamLHFactory.getUrl(videoId) }
        .getOrDefault(youtubeForm)
}

fun playlistIdToUrl(playlistId: String): String = "https://www.youtube.com/playlist?list=$playlistId"

/** Extracts the bare `list=` id from a playlist URL - also NewPipeExtractor's own playlist id for
 * the same URL, so it lines up with whatever id Flow's native UI would save the same playlist
 * under. */
fun playlistUrlToId(url: String?): String? {
    if (url == null) return null
    val idx = url.indexOf("list=")
    if (idx == -1) return null
    return url.substring(idx + 5).substringBefore("&").takeIf { it.isNotEmpty() }
}
