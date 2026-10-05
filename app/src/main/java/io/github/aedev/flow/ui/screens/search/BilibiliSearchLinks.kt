package io.github.aedev.flow.ui.screens.search

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.data.model.Video

/**
 * A pasted Bilibili video or b23.tv short link's video id, or null: [parseYouTubeLink] already
 * covers every YouTube link shape, so this only needs to catch what it does not.
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
