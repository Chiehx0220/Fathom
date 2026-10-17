package io.github.aedev.flow.ui

import io.github.aedev.flow.ui.components.layout.navigation.MediaNavigator

/**
 * Opens [channelArg], or active video's channel if blank. Player may pass stale/blank id (nav placeholder channelId never synced back after metadata loads).
 */
internal fun MediaNavigator.openChannelOrActive(
    channelArg: String,
    activeChannelId: String?,
) = openChannel(channelArg.takeIf { it.isNotBlank() } ?: activeChannelId.orEmpty())
