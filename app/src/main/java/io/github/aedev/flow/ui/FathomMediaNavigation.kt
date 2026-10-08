package io.github.aedev.flow.ui

import io.github.aedev.flow.ui.components.layout.navigation.MediaNavigator

/**
 * Opens [channelArg], or the active video's own channel when it is blank. The player can pass a stale or blank id
 * (the nav placeholder's channelId is never synced back once real metadata loads).
 */
internal fun MediaNavigator.openChannelOrActive(
    channelArg: String,
    activeChannelId: String?,
) = openChannel(channelArg.takeIf { it.isNotBlank() } ?: activeChannelId.orEmpty())
