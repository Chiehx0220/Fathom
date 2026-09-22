package io.github.aedev.flow.data.innertube

/** A channel's display name and avatar, for feeds whose source does not return them with the videos. */
data class ChannelLabel(
    val name: String,
    val avatarUrl: String,
)
