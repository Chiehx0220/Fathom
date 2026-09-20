package io.github.aedev.flow.utils

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliChannelId
import io.github.aedev.flow.bilibili.BilibiliVideoId

/**
 * Ids and links of a service other than YouTube, which fit none of YouTube's URL shapes. Bilibili
 * is the only one; every caller supplies its own fallback for what this cannot read.
 */
fun resolveNonYouTubeChannelId(
    url: String,
    serviceId: Int,
    fallback: () -> String,
): String =
    if (serviceId == BILIBILI_SERVICE_ID) BilibiliChannelId.midOf(url)?.toString() ?: fallback() else fallback()

/** @see resolveNonYouTubeChannelId */
fun resolveNonYouTubeChannelUrl(
    channelId: String,
    serviceId: Int,
    fallback: () -> String,
): String =
    if (serviceId == BILIBILI_SERVICE_ID && BilibiliChannelId.isMid(channelId)) "https://space.bilibili.com/$channelId" else fallback()

/** "BV1xx?p=2" out of a video link, [fallback] when the link is not a Bilibili video. */
fun resolveNonYouTubeStreamId(
    url: String,
    serviceId: Int,
    fallback: () -> String,
): String {
    if (serviceId != BILIBILI_SERVICE_ID) return fallback()
    return BilibiliVideoId.fromUrl(url) ?: fallback()
}
