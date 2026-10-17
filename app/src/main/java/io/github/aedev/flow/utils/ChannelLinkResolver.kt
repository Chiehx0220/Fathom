package io.github.aedev.flow.utils

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliChannelId
import io.github.aedev.flow.bilibili.BilibiliVideoId

/**
 * Non-YouTube ids/links (fit no YouTube URL shape); Bilibili only. Callers supply their own fallback.
 */
fun resolveNonYouTubeChannelId(
    url: String,
    serviceId: Int,
    fallback: () -> String,
): String = if (serviceId == BILIBILI_SERVICE_ID) BilibiliChannelId.midOf(url)?.toString() ?: fallback() else fallback()

/** @see resolveNonYouTubeChannelId */
fun resolveNonYouTubeChannelUrl(
    channelId: String,
    serviceId: Int,
    fallback: () -> String,
): String =
    if (serviceId == BILIBILI_SERVICE_ID && BilibiliChannelId.isMid(channelId)) "https://space.bilibili.com/$channelId" else fallback()

/** "BV1xx?p=2" from a video link; [fallback] if not a Bilibili video. */
fun resolveNonYouTubeStreamId(
    url: String,
    serviceId: Int,
    fallback: () -> String,
): String {
    if (serviceId != BILIBILI_SERVICE_ID) return fallback()
    return BilibiliVideoId.fromUrl(url) ?: fallback()
}
