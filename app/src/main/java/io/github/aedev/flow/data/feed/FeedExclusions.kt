package io.github.aedev.flow.data.feed

import io.github.aedev.flow.data.model.Video

/** Not-interested videos and blocked channels. */
class FeedExclusions(
    val suppressedVideoIds: Set<String> = emptySet(),
    val blockedChannelIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = suppressedVideoIds.isEmpty() && blockedChannelIds.isEmpty()

    fun hides(video: Video): Boolean = video.id in suppressedVideoIds || hidesChannel(video.channelId)

    fun hidesChannel(channelId: String): Boolean = channelId.isNotBlank() && channelId in blockedChannelIds

    fun hidingChannels(channelIds: Set<String>): FeedExclusions =
        if (channelIds.isEmpty()) this else FeedExclusions(suppressedVideoIds, blockedChannelIds + channelIds)

    companion object {
        val NONE = FeedExclusions()
    }
}
