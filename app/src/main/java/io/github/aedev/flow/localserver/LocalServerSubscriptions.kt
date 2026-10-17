package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliLink
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.subscribeOrUpdateInfo
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem

/**
 * Direct native storage read/write, no bridge, no copy. Adapts bare-ID models to URL-keyed [InfoItem].
 * `runBlocking` throughout: handlers on a background thread pool, not coroutines.
 */

internal fun HistoryDbHelper.subscriptionRepository() = SubscriptionRepository.getInstance(appContext)

/** Subscriptions as [InfoItem]s. */
fun HistoryDbHelper.nativeSubscriptions(): List<InfoItem> =
    runBlocking {
        subscriptionRepository().getAllSubscriptions().first().map { sub ->
            val item = ChannelInfoItem(sub.serviceId, channelIdToUrl(sub.channelId, sub.serviceId), sub.channelName)
            if (sub.channelThumbnail.isNotEmpty()) {
                item.thumbnailUrl = sub.channelThumbnail
            }
            item
        }
    }

fun HistoryDbHelper.nativeIsSubscribed(channelUrl: String?): Boolean {
    val channelId = channelUrlToId(channelUrl) ?: return false
    return runBlocking { subscriptionRepository().isSubscribed(channelId).first() }
}

/** Subscribe, or refresh name/avatar; tracked state kept. */
fun HistoryDbHelper.nativeAddSubscription(
    channelUrl: String,
    channelName: String?,
    channelAvatar: String?,
) {
    val channelId = channelUrlToId(channelUrl) ?: return
    val serviceId =
        if (BilibiliLink.isBilibili(channelUrl)) {
            LocalServerBilibili.serviceId
        } else {
            LocalServerMedia.serviceIdOfUrl(channelUrl)
        }
    runBlocking {
        subscriptionRepository().subscribeOrUpdateInfo(channelId, channelName ?: "", channelAvatar ?: "", serviceId)
    }
}

fun HistoryDbHelper.nativeRemoveSubscription(channelUrl: String) {
    val channelId = channelUrlToId(channelUrl) ?: return
    runBlocking { subscriptionRepository().unsubscribe(channelId) }
}

/** FlowNeuroEngine block list, direct; ranking already excludes blocked channels (native recommendations too). */
fun HistoryDbHelper.nativeIsChannelBlocked(channelUrl: String?): Boolean {
    val channelId = channelUrlToId(channelUrl) ?: return false
    return runBlocking {
        ensureFlowNeuroInitialized()
        FlowNeuroEngine.getInstance(appContext).getBlockedChannels().contains(channelId)
    }
}

fun HistoryDbHelper.nativeBlockedChannelIds(): Set<String> =
    runBlocking {
        ensureFlowNeuroInitialized()
        FlowNeuroEngine.getInstance(appContext).getBlockedChannels()
    }

fun HistoryDbHelper.nativeBlockChannel(channelUrl: String) {
    val channelId = channelUrlToId(channelUrl) ?: return
    runBlocking {
        ensureFlowNeuroInitialized()
        FlowNeuroEngine.blockChannel(channelId)
    }
}

fun HistoryDbHelper.nativeUnblockChannel(channelUrl: String) {
    val channelId = channelUrlToId(channelUrl) ?: return
    runBlocking {
        ensureFlowNeuroInitialized()
        FlowNeuroEngine.unblockChannel(channelId)
    }
}
