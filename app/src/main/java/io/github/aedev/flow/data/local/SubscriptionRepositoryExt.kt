package io.github.aedev.flow.data.local

import kotlinx.coroutines.flow.first

/**
 * Subscribe, or refresh display info if subscribed, keeping [ChannelSubscription.subscribedAt]/
 * [ChannelSubscription.lastVideoId]/notification state (a fresh [ChannelSubscription] + [subscribe] would reset them).
 * For passive re-confirmation on channel visit (e.g. Local Server).
 */
suspend fun SubscriptionRepository.subscribeOrUpdateInfo(
    channelId: String,
    channelName: String,
    channelThumbnail: String,
    serviceId: Int = 0,
) {
    val existing = getSubscription(channelId).first()
    subscribe(
        existing?.copy(channelName = channelName, channelThumbnail = channelThumbnail)
            ?: ChannelSubscription(channelId, channelName, channelThumbnail, serviceId = serviceId),
    )
}
