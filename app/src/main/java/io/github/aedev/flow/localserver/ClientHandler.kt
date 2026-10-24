package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliLink
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedTimestamps
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.InetAddress
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

// Handler groups: extension functions in sibling files.
internal class ClientHandler(
    internal val peer: InetAddress,
    internal val dbHelper: HistoryDbHelper,
    private val executorService: ExecutorService,
    internal val pairing: Pairing,
) {
    internal val remoteAddress: String? get() = peer.hostAddress
    internal val remoteIsLoopback: Boolean get() = peer.isLoopbackAddress

    // Subscribed channels' uploads → one newest-first feed (cap 60); channel resolved by own URL (subscriptions mix services).
    internal fun fetchSubscriptionFeed(channels: List<InfoItem>?): List<InfoItem> {
        val feed = ArrayList<InfoItem>()
        if (channels == null || channels.isEmpty()) {
            return feed
        }
        val futures = ArrayList<Future<List<InfoItem>>>()
        for (channel in channels) {
            val url = channel.url
            // Avatar from subscription; upload listings carry none.
            val subscribedAvatarUrl = channel.thumbnailUrl?.takeIf { it.isNotBlank() }
            futures.add(
                executorService.submit(
                    Callable {
                        val uploads =
                            if (BilibiliLink.isBilibili(url)) {
                                LocalServerBilibili.uploads(
                                    dbHelper.appContext,
                                    url,
                                    channel.name.orEmpty(),
                                    subscribedAvatarUrl.orEmpty(),
                                )
                            } else {
                                LocalServerMedia.fetchChannelUploads(url)
                            }
                        if (!subscribedAvatarUrl.isNullOrEmpty()) {
                            for (upload in uploads) {
                                if (upload is StreamInfoItem && upload.uploaderAvatarUrl.isNullOrBlank()) {
                                    upload.uploaderAvatarUrl = subscribedAvatarUrl
                                }
                            }
                        }
                        uploads
                    },
                ),
            )
        }
        for ((index, future) in futures.withIndex()) {
            try {
                // Bilibili first request of a session: cookies + signing key setup, slower.
                val waitSeconds = if (BilibiliLink.isBilibili(channels[index].url)) 20L else 5L
                val res = future.get(waitSeconds, TimeUnit.SECONDS)
                if (res != null) {
                    feed.addAll(res)
                }
            } catch (e: Exception) {
                serverLog("Future timeout/error fetching subscription feed: " + e.message)
            }
        }
        val now = System.currentTimeMillis()
        return feed
            .map { it to uploadTimeOf(it, now) }
            .sortedByDescending { it.second ?: Long.MIN_VALUE }
            .take(60)
            .map { it.first }
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

    internal fun getServiceId(params: Map<String, String>): Int {
        val raw = params["serviceId"]
        if (raw != null) {
            try {
                val id = raw.trim().toInt()
                if (LocalServerMedia.isSupportedService(id)) {
                    return id
                }
            } catch (ignored: NumberFormatException) {
            }
        }
        return LocalServerMedia.SERVICE_YOUTUBE
    }

    @Throws(Exception::class)
    internal fun handleSearchHistory(
        reply: Reply,
        params: Map<String, String>,
    ) {
        val deleteQuery = params["delete"]
        if (!deleteQuery.isNullOrEmpty()) {
            dbHelper.nativeDeleteSearchQuery(deleteQuery)
            reply.sendJson("{\"status\":\"success\"}")
            return
        }
        reply.sendJson(JSONArray(dbHelper.nativeSearchHistory()).toString())
    }

    internal fun filterItems(items: List<InfoItem>): List<InfoItem> {
        val hideWatched = dbHelper.nativeHideWatched()
        val hideShorts = dbHelper.nativeHideShorts()
        val blockedChannelIds = dbHelper.nativeBlockedChannelIds()

        val filtered = ArrayList<InfoItem>()
        val watchedUrls: Set<String> = if (hideWatched) dbHelper.nativeWatchedUrls() else emptySet()

        for (item in items) {
            if (hideWatched && watchedUrls.contains(item.url)) {
                continue
            }

            if (hideShorts && item is StreamInfoItem) {
                if (item.duration > 0 && item.duration <= 120) {
                    continue
                }
            }

            if (item is StreamInfoItem && blockedChannelIds.isNotEmpty()) {
                val channelId = channelUrlToId(item.uploaderUrl)
                if (channelId != null && blockedChannelIds.contains(channelId)) continue
            }

            filtered.add(item)
        }
        return filtered
    }

    // FlowNeuro signals from handleApiVideo and progress endpoint; best-effort, never affects playback/DB write.
    internal fun reportFlowNeuroClick(
        info: StreamInfo,
        serviceId: Int,
    ) {
        dbHelper.reportFlowNeuroInteraction(info, serviceId, InteractionType.CLICK)
    }

    // Ranking: native FlowNeuroEngine (rankWithFlowNeuro()), used by handleApiRecommendations(), not handleApiHome().
    internal fun applyFlowNeuroRanking(
        items: List<InfoItem>,
        serviceId: Int,
    ): List<InfoItem> = dbHelper.rankWithFlowNeuro(items, serviceId)
}
