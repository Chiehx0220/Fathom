package io.github.aedev.flow.ui.screens.player.stage

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.player.danmaku.DanmakuComment
import io.github.aedev.flow.player.danmaku.DanmakuPosition

private const val SCROLL_DURATION_MS = 8_000L
private const val FIXED_DURATION_MS = 4_000L
private const val SCROLL_LANES = 14
private const val FIXED_LANES = 4

internal class ActiveDanmaku(
    val comment: DanmakuComment,
    val spawnMs: Long,
    val lane: Int,
)

/**
 * The comments on screen and the lanes they travel in, on whatever clock its layer keeps: playback
 * position for a video's danmaku, an accumulated play time for a live room's. [active] is snapshot
 * state read only while drawing, so a comment appearing or leaving redraws without recomposing.
 */
internal class DanmakuField {
    val active = mutableStateListOf<ActiveDanmaku>()

    private val scrollLaneUntil = LongArray(SCROLL_LANES)
    private val topLaneUntil = LongArray(FIXED_LANES)
    private val bottomLaneUntil = LongArray(FIXED_LANES)

    fun spawn(
        comment: DanmakuComment,
        nowMs: Long,
    ) {
        val duration = durationOf(comment)
        val lane =
            when (comment.position) {
                DanmakuPosition.TOP -> pickLane(topLaneUntil, nowMs, duration)
                DanmakuPosition.BOTTOM -> pickLane(bottomLaneUntil, nowMs, duration)
                DanmakuPosition.SCROLL -> pickLane(scrollLaneUntil, nowMs, duration)
            }
        active.add(ActiveDanmaku(comment, nowMs, lane))
    }

    fun expire(nowMs: Long) {
        active.removeAll { nowMs - it.spawnMs > durationOf(it.comment) }
    }

    fun clear() {
        active.clear()
        scrollLaneUntil.fill(0L)
        topLaneUntil.fill(0L)
        bottomLaneUntil.fill(0L)
    }

    fun DrawScope.drawComments(
        textMeasurer: TextMeasurer,
        nowMs: Long,
    ) {
        val widthDp = size.width / density
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
                    val progress = ((nowMs - entry.spawnMs).toFloat() / SCROLL_DURATION_MS).coerceIn(0f, 1f)
                    val startX = size.width
                    val endX = -layout.size.width.toFloat()
                    val x = startX + (endX - startX) * progress
                    drawText(layout, topLeft = Offset(x, entry.lane * laneHeightPx))
                }

                DanmakuPosition.TOP, DanmakuPosition.BOTTOM -> {
                    val progress = ((nowMs - entry.spawnMs).toFloat() / FIXED_DURATION_MS).coerceIn(0f, 1f)
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

    private fun durationOf(comment: DanmakuComment) =
        if (comment.position == DanmakuPosition.SCROLL) SCROLL_DURATION_MS else FIXED_DURATION_MS

    /** The first free lane, else the one that frees up soonest. */
    private fun pickLane(
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
}
