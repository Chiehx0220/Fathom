package io.github.aedev.flow.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.ui.components.shared.ProvidePlatformBadge

/** [SearchScreen] queries one service at a time; result cards omit the service badge. */
@Composable
fun SingleServiceSearchScreen(
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onShortsQueue: (ShortsQueueSource) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = ProvidePlatformBadge(show = false) {
    SearchScreen(onVideoClick, onChannelClick, onPlaylistClick, onShortsQueue, onBack, modifier)
}
