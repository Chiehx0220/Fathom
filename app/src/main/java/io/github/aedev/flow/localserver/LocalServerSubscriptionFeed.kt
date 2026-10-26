package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedTimestamps
import kotlinx.coroutines.flow.first
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

// Subscribed channels' uploads → one newest-first feed (cap 60); channel resolved by own URL (subscriptions mix services).
internal fun RequestContext.subscriptionFeed(channels: List<InfoItem>?): List<InfoItem> {
    if (channels.isNullOrEmpty()) return emptyList()
    val pending = channels.map { channel -> channel to executor.submit(Callable { uploadsOf(channel) }) }
    val feed = ArrayList<InfoItem>()
    for ((channel, future) in pending) {
        try {
            // Bilibili first request of a session: cookies + signing key setup, slower.
            val waitSeconds = if (BilibiliRef.isBilibiliUrl(channel.url)) BILIBILI_WAIT_SECONDS else WAIT_SECONDS
            feed.addAll(future.get(waitSeconds, TimeUnit.SECONDS))
        } catch (e: Exception) {
            serverLog("Future timeout/error fetching subscription feed: " + e.message)
        }
    }
    val now = System.currentTimeMillis()
    return feed
        .map { it to uploadTimeOf(it, now) }
        .sortedByDescending { it.second ?: Long.MIN_VALUE }
        .take(FEED_LIMIT)
        .map { it.first }
}

private const val FEED_LIMIT = 60
private const val WAIT_SECONDS = 5L
private const val BILIBILI_WAIT_SECONDS = 20L

private fun RequestContext.uploadsOf(channel: InfoItem): List<InfoItem> {
    val url = channel.url
    // Avatar from subscription; upload listings carry none.
    val subscribedAvatarUrl = channel.thumbnailUrl?.takeIf { it.isNotBlank() }
    val uploads =
        if (BilibiliRef.isBilibiliUrl(url)) {
            LocalServerBilibili.uploads(dbHelper.appContext, url, channel.name.orEmpty(), subscribedAvatarUrl.orEmpty())
        } else {
            LocalServerMedia.fetchChannelUploads(url)
        }
    if (subscribedAvatarUrl != null) {
        for (upload in uploads) {
            if (upload is StreamInfoItem && upload.uploaderAvatarUrl.isNullOrBlank()) upload.uploaderAvatarUrl = subscribedAvatarUrl
        }
    }
    return uploads
}

/** Upload time, epoch ms: exact date, else relative text against [now]. Null sorts last. */
private fun uploadTimeOf(
    item: InfoItem,
    now: Long,
): Long? {
    val stream = item as? StreamInfoItem ?: return null
    stream.uploadDate?.let { return it.offsetDateTime().toInstant().toEpochMilli() }
    return stream.textualUploadDate?.let { SubscriptionFeedTimestamps.parseRelativeTime(it, now) }
}
