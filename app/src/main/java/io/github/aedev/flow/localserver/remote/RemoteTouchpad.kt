@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.LocalHttpServer
import kotlin.math.hypot
import kotlin.math.min

private const val POINTER_GAIN_MIN = 1.6f
private const val POINTER_GAIN_MAX = 3.2f
private const val SCROLL_SPEED = 3f
private const val SEND_INTERVAL_MS = 24L
private const val TAP_MAX_MS = 250L

/** A touchpad for anything small on the page: one finger moves the pointer, a tap clicks, two fingers scroll. */
@Composable
internal fun TouchpadMode(
    state: LocalHttpServer.RemoteState,
    send: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Touchpad(send, Modifier.weight(1f).fillMaxWidth())
        ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
            wideKey(Icons.AutoMirrored.Filled.ArrowBack, R.string.remote_back) { send("back") }
            wideKey(
                if (state.hasVideo && !state.paused) Icons.Default.Pause else Icons.Default.PlayArrow,
                R.string.remote_play_pause,
            ) { send("play_pause") }
        }
    }
}

@Composable
private fun Touchpad(
    send: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = rememberKeyTick()
    var touches by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .drawBehind { touches.forEach { drawCircle(glow, radius = 56.dp.toPx(), center = it) } }
                .touchpadGestures(send, tick) { touches = it },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.TouchApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.remote_touchpad_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/**
 * One gesture loop for the whole pad, so a tap, a drag and a two-finger scroll can never fight over the same touch.
 * Travel is sent in batches and the fractions carried over, so slow movement is not lost to rounding and the last
 * bit of a stroke is sent when the finger lifts.
 */
private fun Modifier.touchpadGestures(
    send: (String) -> Unit,
    tick: () -> Unit,
    onTouch: (List<Offset>) -> Unit,
): Modifier =
    pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startTime = down.uptimeMillis
            var travelled = 0f
            var scrolled = false
            var twoFingers = false
            var pendingX = 0f
            var pendingY = 0f
            var pendingScroll = 0f
            var lastSent = startTime
            var endTime = startTime
            onTouch(listOf(down.position))

            fun flush() {
                val x = pendingX.toInt()
                val y = pendingY.toInt()
                if (x != 0 || y != 0) {
                    send("pointer_move:$x,$y")
                    pendingX -= x
                    pendingY -= y
                }
                val s = pendingScroll.toInt()
                if (s != 0) {
                    send("pointer_scroll:$s")
                    pendingScroll -= s
                }
            }

            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) twoFingers = true
                // The event that lifts the last finger has nothing pressed: averaging it would give NaN and void the tap.
                val dx = if (pressed.isEmpty()) 0f else pressed.map { it.positionChange().x }.average().toFloat()
                val dy = if (pressed.isEmpty()) 0f else pressed.map { it.positionChange().y }.average().toFloat()
                onTouch(pressed.map { it.position })
                travelled += hypot(dx, dy)
                if (travelled > slop) {
                    if (twoFingers) {
                        // Dragging up moves the page up under the fingers, like a phone: that is scrolling down.
                        pendingScroll -= dy * SCROLL_SPEED
                        scrolled = true
                    } else {
                        // Faster strokes travel further, so a flick crosses the page and a nudge stays precise.
                        val speed = hypot(dx, dy)
                        val gain = POINTER_GAIN_MIN + (POINTER_GAIN_MAX - POINTER_GAIN_MIN) * min(speed / 24f, 1f)
                        pendingX += dx * gain
                        pendingY += dy * gain
                    }
                    pressed.forEach { if (it.positionChange() != Offset.Zero) it.consume() }
                }
                val now = event.changes.first().uptimeMillis
                endTime = now
                if (now - lastSent >= SEND_INTERVAL_MS) {
                    flush()
                    lastSent = now
                }
            } while (event.changes.any { it.pressed })

            flush()
            onTouch(emptyList())
            if (travelled <= slop && !twoFingers && !scrolled && endTime - startTime <= TAP_MAX_MS) {
                tick()
                send("pointer_click")
            }
        }
    }
