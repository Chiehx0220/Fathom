package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliChannelId
import io.github.aedev.flow.data.model.isYouTubeServiceId
import org.schabi.newpipe.extractor.ServiceList

/*
 * Converts between the local server's URL-keyed InfoItems and native storage's bare-id keys, for the
 * two services Flow has: YouTube and Bilibili.
 */

/** Bare channel id -> URL: YouTube's canonical form, or a Bilibili space page for its numeric id. */
fun channelIdToUrl(channelId: String, serviceId: Int = ServiceList.YouTube.serviceId): String =
    if (LocalServerBilibili.isBilibili(serviceId)) {
        LocalServerBilibili.channelUrl(channelId)
    } else {
        "https://www.youtube.com/channel/$channelId"
    }

/**
 * Channel URL -> bare id. Parses `/channel/UC...`, `/@handle`, `/c/name` and `/user/name` for
 * YouTube, and a Bilibili space page or bare number for Bilibili. Null for anything else.
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
    return BilibiliChannelId.midOf(normalized)?.toString()
}

/**
 * Bare video id -> URL: YouTube's canonical form, or a Bilibili video link. Rendering keys
 * everything off `item.url`, so it must be a link the server can read the id back out of.
 */
fun videoIdToUrl(videoId: String, serviceId: Int = ServiceList.YouTube.serviceId): String =
    if (serviceId.isYouTubeServiceId) "https://www.youtube.com/watch?v=$videoId" else LocalServerBilibili.videoUrl(videoId)

fun playlistIdToUrl(playlistId: String): String = "https://www.youtube.com/playlist?list=$playlistId"

/** Bare `list=` id from a playlist URL - matches NewPipeExtractor's own playlist id for it. */
fun playlistUrlToId(url: String?): String? {
    if (url == null) return null
    val idx = url.indexOf("list=")
    if (idx == -1) return null
    return url.substring(idx + 5).substringBefore("&").takeIf { it.isNotEmpty() }
}
