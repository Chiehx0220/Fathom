package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.data.model.isYouTubeServiceId
import org.schabi.newpipe.extractor.ServiceList

/*
 * Local-server URL-keyed InfoItems ↔ native bare-id keys; YouTube and Bilibili.
 */

/** Bare channel id → URL: YouTube canonical, or Bilibili space page for numeric id. */
fun channelIdToUrl(
    channelId: String,
    serviceId: Int = ServiceList.YouTube.serviceId,
): String =
    if (LocalServerBilibili.isBilibili(serviceId)) {
        LocalServerBilibili.channelUrl(channelId)
    } else {
        "https://www.youtube.com/channel/$channelId"
    }

/** Channel URL → bare id (YouTube, Bilibili); else null. */
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
    return BilibiliRef.Uploader.ofLink(normalized)?.id
}

/** Bare video id → YouTube canonical or Bilibili video URL; `item.url` must parse back to the id. */
fun videoIdToUrl(
    videoId: String,
    serviceId: Int = ServiceList.YouTube.serviceId,
): String {
    // Rows from before id normalisation hold a whole Bilibili link as id: reads as its id.
    val id = LocalServerMedia.getVideoId(videoId)
    val isYouTube = serviceIdOfVideo(id, serviceId).isYouTubeServiceId
    return if (isYouTube) "https://www.youtube.com/watch?v=$id" else LocalServerBilibili.videoUrl(id)
}

fun playlistIdToUrl(playlistId: String): String = "https://www.youtube.com/playlist?list=$playlistId"

/** Bare `list=` id of a playlist URL; matches NewPipeExtractor's playlist id. */
fun playlistUrlToId(url: String?): String? {
    if (url == null) return null
    val idx = url.indexOf("list=")
    if (idx == -1) return null
    return url.substring(idx + 5).substringBefore("&").takeIf { it.isNotEmpty() }
}
