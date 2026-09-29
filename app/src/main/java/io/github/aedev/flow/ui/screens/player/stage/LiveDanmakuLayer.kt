package io.github.aedev.flow.ui.screens.player.stage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.danmaku.toDanmakuComment

// A hot room posts far more than a screen can show; past this many on screen, newcomers are dropped.
private const val MAX_ON_SCREEN = 60

/**
 * A live Bilibili room's chat over the video. Live messages have no playback position, so the field
 * runs on a play clock that only advances while the player is playing: pausing freezes the comments,
 * and messages that arrive meanwhile are dropped rather than queued into a burst on resume.
 *
 * The chat socket is opened by the collection below and closed with it, so it is open only while this
 * layer is composed and enabled, and the frame loop runs only while something is on screen and playing.
 */
@Composable
internal fun BoxScope.LiveDanmakuLayer(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val manager = EnhancedPlayerManager.getInstance()
    val roomId by manager.liveDanmakuRoom.collectAsStateWithLifecycle()
    val id = roomId
    if (!enabled || id == null) return

    val context = LocalContext.current
    val playbackState by manager.playerState.collectAsStateWithLifecycle()
    val isPlaying by rememberUpdatedState(playbackState.isPlaying)

    val field = remember(id) { DanmakuField() }
    val textMeasurer = rememberTextMeasurer()
    var clockMs by remember(id) { mutableLongStateOf(0L) }
    val hasActive by remember(field) { derivedStateOf { field.active.isNotEmpty() } }

    LaunchedEffect(id) {
        bilibiliApi(context).liveMessages(id).collect { message ->
            if (isPlaying && field.active.size < MAX_ON_SCREEN) field.spawn(message.toDanmakuComment(), clockMs)
        }
    }

    LaunchedEffect(playbackState.isPlaying, hasActive) {
        if (!playbackState.isPlaying || !hasActive) return@LaunchedEffect
        var lastFrameMs = -1L
        while (true) {
            withFrameMillis { frameMs ->
                if (lastFrameMs >= 0L) {
                    clockMs += frameMs - lastFrameMs
                    field.expire(clockMs)
                }
                lastFrameMs = frameMs
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        with(field) { drawComments(textMeasurer, clockMs) }
    }
}
