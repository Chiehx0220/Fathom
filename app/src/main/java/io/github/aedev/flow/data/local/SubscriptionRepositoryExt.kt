package io.github.aedev.flow.data.local

import kotlinx.coroutines.flow.first

/**
 * Subscribe to a channel, or refresh its display info if already subscribed - without
 * resetting [ChannelSubscription.subscribedAt]/[ChannelSubscription.lastVideoId]/
 * notification state back to defaults the way constructing a fresh [ChannelSubscription]
 * and calling [subscribe] would. Used by callers (e.g. Local Server) that re-confirm a
 * subscription passively on every visit to a channel, not just on an explicit subscribe action.
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
