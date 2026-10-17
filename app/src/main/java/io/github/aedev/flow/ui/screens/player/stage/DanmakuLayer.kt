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

/**
 * Bilibili danmaku overlay: native-player counterpart of Local Server JS version (app/js/player.js danmaku layer).
 * On-screen progress = f(playback position), not wall clock: pause freezes comments.
 *
 * [currentPositionMs] updates ~250ms (PlayerPositionEffects.kt): choppy. Smoothed by linear extrapolation from last real position at player wall-clock rate
 * (playing only), corrected on each fresh value. Zero cost while paused.
 *
 * On-screen duration = Bilibili default ([DanmakuField]); data has no duration. Live chat: [LiveDanmakuLayer].
 */
@Composable
internal fun BoxScope.DanmakuLayer(
    currentPositionMs: Long,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val comments by EnhancedPlayerManager.getInstance().danmakuComments.collectAsStateWithLifecycle()
    if (!enabled || comments.isEmpty()) return

    val playbackState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()

    // anchorPositionMs/anchorAtElapsedMs = (real position, when); smoothed = anchor + elapsed wall time × speed.
    // Re-anchored on fresh real position (no drift) and on play/pause/speed change (no jump after pause).
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
