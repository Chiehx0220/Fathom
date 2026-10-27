package io.github.aedev.flow.ui.screens.player.stage

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.player.EnhancedPlayerManager
import kotlin.math.abs

// Tick later than this = backlog (e.g. backgrounded), not a burst.
private const val STALE_THRESHOLD_MS = 1_200L
private const val SEEK_JUMP_THRESHOLD_MS = 1_500L

/** Bilibili danmaku overlay: progress = f(position), pause freezes; ~250ms position ticks extrapolated at wall-clock rate. */
@Composable
internal fun BoxScope.DanmakuLayer(
    currentPositionMs: Long,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val comments by EnhancedPlayerManager
        .getInstance()
        .danmaku.comments
        .collectAsStateWithLifecycle()
    if (!enabled || comments.isEmpty()) return

    val playbackState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()

    // anchor = (real position, when); smoothed = anchor + elapsed × speed; re-anchored on fresh position and play/pause/speed.
    var anchorPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    var anchorAtElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var smoothedPositionMs by remember { mutableLongStateOf(currentPositionMs) }

    LaunchedEffect(currentPositionMs) {
        anchorPositionMs = currentPositionMs
        anchorAtElapsedMs = SystemClock.elapsedRealtime()
        smoothedPositionMs = currentPositionMs
    }

    LaunchedEffect(playbackState.isPlaying, playbackState.playbackSpeed) {
        if (!playbackState.isPlaying) return@LaunchedEffect
        anchorAtElapsedMs = SystemClock.elapsedRealtime()
        while (true) {
            withFrameMillis {
                val elapsedMs = SystemClock.elapsedRealtime() - anchorAtElapsedMs
                smoothedPositionMs = anchorPositionMs + (elapsedMs * playbackState.playbackSpeed).toLong()
            }
        }
    }

    val sorted = remember(comments) { comments.sortedBy { it.timeMs } }
    val textMeasurer = rememberTextMeasurer()
    var nextIndex by remember(sorted) { mutableIntStateOf(0) }
    var lastPositionMs by remember(sorted) { mutableLongStateOf(-1L) }
    val field = remember(sorted) { DanmakuField() }

    fun resync(posMs: Long) {
        field.clear()
        var idx = 0
        while (idx < sorted.size && sorted[idx].timeMs < posMs) idx++
        nextIndex = idx
    }

    LaunchedEffect(smoothedPositionMs, sorted) {
        val posMs = smoothedPositionMs
        val jumped = lastPositionMs >= 0L && abs(posMs - lastPositionMs) > SEEK_JUMP_THRESHOLD_MS
        if (lastPositionMs < 0L || jumped) {
            resync(posMs)
        } else {
            while (nextIndex < sorted.size && sorted[nextIndex].timeMs <= posMs) {
                val comment = sorted[nextIndex]
                if (posMs - comment.timeMs < STALE_THRESHOLD_MS) field.spawn(comment, posMs)
                nextIndex++
            }
        }
        lastPositionMs = posMs
        field.expire(posMs)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        with(field) { drawComments(textMeasurer, smoothedPositionMs) }
    }
}
