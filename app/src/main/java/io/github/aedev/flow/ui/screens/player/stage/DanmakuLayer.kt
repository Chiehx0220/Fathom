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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.danmaku.DanmakuComment
import io.github.aedev.flow.player.danmaku.DanmakuPosition
import kotlin.math.abs

private const val SCROLL_DURATION_MS = 8_000L
private const val FIXED_DURATION_MS = 4_000L

// A tick that arrives more than this late for its scheduled time is treated as backlog (e.g. the
// app was backgrounded) rather than spawned in a sudden burst.
private const val STALE_THRESHOLD_MS = 1_200L
private const val SEEK_JUMP_THRESHOLD_MS = 1_500L
private const val SCROLL_LANES = 14
private const val FIXED_LANES = 4

private class ActiveDanmaku(val comment: DanmakuComment, val spawnMs: Long, val lane: Int)

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
 * BulletCommentsInfoItem.getLastingTime() always returns -1 (an extractor-library bug, not this
 * app's), so on-screen duration is hardcoded to Bilibili's own typical defaults instead of coming
 * from the data, matching Local Server's own /danmaku route.
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
    val active = remember(sorted) { mutableStateListOf<ActiveDanmaku>() }
    val scrollLaneUntil = remember(sorted) { LongArray(SCROLL_LANES) }
    val topLaneUntil = remember(sorted) { LongArray(FIXED_LANES) }
    val bottomLaneUntil = remember(sorted) { LongArray(FIXED_LANES) }

    fun pickLane(
        untilArr: LongArray,
        now: Long,
        dur: Long,
    ): Int {
        for (i in untilArr.indices) {
            if (untilArr[i] <= now) {
                untilArr[i] = now + dur
                return i
            }
        }
        var idx = 0
        for (i in 1 until untilArr.size) if (untilArr[i] < untilArr[idx]) idx = i
        untilArr[idx] = now + dur
        return idx
    }

    fun durationFor(comment: DanmakuComment) = if (comment.position == DanmakuPosition.SCROLL) SCROLL_DURATION_MS else FIXED_DURATION_MS

    fun resync(posMs: Long) {
        active.clear()
        scrollLaneUntil.fill(0L)
        topLaneUntil.fill(0L)
        bottomLaneUntil.fill(0L)
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
                if (posMs - comment.timeMs < STALE_THRESHOLD_MS) {
                    val dur = durationFor(comment)
                    val lane =
                        when (comment.position) {
                            DanmakuPosition.TOP -> pickLane(topLaneUntil, posMs, dur)
                            DanmakuPosition.BOTTOM -> pickLane(bottomLaneUntil, posMs, dur)
                            DanmakuPosition.SCROLL -> pickLane(scrollLaneUntil, posMs, dur)
                        }
                    active.add(ActiveDanmaku(comment, posMs, lane))
                }
                nextIndex++
            }
        }
        lastPositionMs = posMs
        active.removeAll { posMs - it.spawnMs > durationFor(it.comment) }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val widthDp = size.width / density
        val posMs = smoothedPositionMs
        active.forEach { entry ->
            val comment = entry.comment
            val fontSizeSp = (widthDp * comment.relativeFontSize * 0.028f).coerceIn(14f, 30f)
            val layout =
                textMeasurer.measure(
                    text = comment.text,
                    style =
                        TextStyle(
                            fontSize = fontSizeSp.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(comment.argbColor),
                        ),
                )
            val laneHeightPx = fontSizeSp.sp.toPx() * 1.6f
            when (comment.position) {
                DanmakuPosition.SCROLL -> {
                    val progress = ((posMs - entry.spawnMs).toFloat() / SCROLL_DURATION_MS).coerceIn(0f, 1f)
                    val startX = size.width
                    val endX = -layout.size.width.toFloat()
                    val x = startX + (endX - startX) * progress
                    drawText(layout, topLeft = Offset(x, entry.lane * laneHeightPx))
                }
                DanmakuPosition.TOP, DanmakuPosition.BOTTOM -> {
                    val progress = ((posMs - entry.spawnMs).toFloat() / FIXED_DURATION_MS).coerceIn(0f, 1f)
                    val alpha =
                        when {
                            progress < 0.1f -> progress / 0.1f
                            progress > 0.85f -> (1f - progress) / 0.15f
                            else -> 1f
                        }.coerceIn(0f, 1f)
                    val x = (size.width - layout.size.width) / 2f
                    val y =
                        if (comment.position == DanmakuPosition.TOP) {
                            8f + entry.lane * laneHeightPx
                        } else {
                            size.height - 8f - (entry.lane + 1) * laneHeightPx
                        }
                    drawText(layout, topLeft = Offset(x, y), alpha = alpha)
                }
            }
        }
    }
}
