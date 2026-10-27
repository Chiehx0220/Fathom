package io.github.aedev.flow.data.local

import kotlinx.coroutines.flow.first

/** Subscribe or refresh display info, keeping subscribedAt, lastVideoId, notification state. */
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
