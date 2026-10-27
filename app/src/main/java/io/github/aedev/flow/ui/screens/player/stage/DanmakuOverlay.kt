package io.github.aedev.flow.ui.screens.player.stage

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.supportsBulletComments
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState

// VideoStage hook: danmaku (VOD) or live chat layer for videos of a danmaku service.
@Composable
internal fun BoxScope.DanmakuOverlay(
    video: Video,
    isLive: Boolean,
    screenState: PlayerScreenState,
    modifier: Modifier = Modifier,
) {
    if (!video.supportsBulletComments) return
    if (isLive) {
        LiveDanmakuLayer(enabled = screenState.danmakuEnabled, modifier = modifier)
    } else {
        DanmakuLayer(currentPositionMs = screenState.currentPosition, enabled = screenState.danmakuEnabled, modifier = modifier)
    }
}
