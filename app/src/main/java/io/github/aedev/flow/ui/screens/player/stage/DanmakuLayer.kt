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

// A tick that arrives more than this late for its scheduled time is treated as backlog (e.g. the
// app was backgrounded) rather than spawned in a sudden burst.
private const val STALE_THRESHOLD_MS = 1_200L
private const val SEEK_JUMP_THRESHOLD_MS = 1_500L

/**
 * Bilibili danmaku overlay - the native-player counterpart of Local Server's own JS-driven version
 * (app/js/player.js's danmaku layer). Every comment's on-screen progress is a function of playback
 * position, not wall-clock time, so pausing the video freezes comments for free.
 *
 * [currentPositionMs] itself only updates on a ~250ms cadence (see PlayerPositionEffects.kt), which
 * produced visibly stepped/choppy motion - this smooths it by linearly extrapolating from the last
 * known real position at the player's own wall-clock rate between updates (while actually playing),
 * correcting back to the real value every time a fresh one arrives. The extrapolation only runs
 * while [EnhancedPlayerState.isPlaying] is true, so it costs nothing while paused.
 *
 * On-screen duration is Bilibili's own typical default (see [DanmakuField]), because the danmaku
 * data carries no lasting time. A live room's chat is [LiveDanmakuLayer].
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

    // anchorPositionMs/anchorAtElapsedMs pin down "what the real position was, and when" - the
    // smoothed value is then that anchor plus wall-clock time elapsed since, scaled by playback
    // speed. Re-anchored both whenever a fresh real position arrives (keeps drift from
    // accumulating) and whenever play/pause or speed changes (so resuming after a pause doesn't
    // jump forward by however long the pause lasted).
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
