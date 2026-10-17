package io.github.aedev.flow.ui.screens.search

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.data.model.Video

/**
 * Video id of a pasted Bilibili video / b23.tv link, or null; [parseYouTubeLink] covers YouTube shapes: only the rest here.
 */
internal fun resolvePastedBilibiliVideoLink(url: String): String? {
    if (!url.startsWith("http")) return null
    return BilibiliVideoId.fromUrl(url)
}

internal fun sharedBilibiliVideo(
    videoId: String,
    title: String,
) = Video(
    id = videoId,
    title = title,
    channelName = title,
    channelId = "",
    thumbnailUrl = "",
    duration = 0,
    viewCount = 0L,
    uploadDate = "",
    serviceId = BILIBILI_SERVICE_ID,
)
