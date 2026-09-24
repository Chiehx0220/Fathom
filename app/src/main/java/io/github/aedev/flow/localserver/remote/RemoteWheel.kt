@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.withTimeoutOrNull

// Finger travel (dp) that counts as one press while swiping over the wheel.
private const val SWIPE_STEP_DP = 40f

private enum class WheelKey(val command: String) {
    UP("key:up"),
    DOWN("key:down"),
    LEFT("key:left"),
    RIGHT("key:right"),
}

/**
 * The direction wheel. The whole disc is the touch surface, so it can be used without looking: a tap presses the direction
 * nearest the touch, a swipe presses the way it points once per step so a long swipe keeps moving, and holding opens the
 * selected card's options. The OK key in the middle is a standard button and behaves like every other key.
 */
@Composable
internal fun DirectionWheel(
    send: (String) -> Unit,
    wheelSize: Dp,
) {
    val okSize = wheelSize * 0.4f
    val haptic = LocalHapticFeedback.current
    val tick = rememberKeyTick()

    Box(
        modifier =
            Modifier
                .size(wheelSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(okSize) {
                    val slop = viewConfiguration.touchSlop
                    val longPress = viewConfiguration.longPressTimeoutMillis
                    val step = SWIPE_STEP_DP.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val touched = wheelKeyAt(down.position - centre)
                        var lastTime = down.uptimeMillis
                        var travelled = 0f
                        var accX = 0f
                        var accY = 0f
                        var swiping = false
                        var held = false
                        while (true) {
                            val wait = if (swiping || held) null else longPress - (lastTime - down.uptimeMillis)
                            val event = if (wait == null) awaitPointerEvent() else withTimeoutOrNull(wait.coerceAtLeast(1)) { awaitPointerEvent() }
                            if (event == null) {
                                held = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                send("key:menu")
                                continue
                            }
                            val change = event.changes.first()
                            lastTime = change.uptimeMillis
                            if (!change.pressed) {
                                if (!swiping && !held) {
                                    tick()
                                    send(touched.command)
                                }
                                break
                            }
                            val move = change.positionChange()
                            travelled += abs(move.x) + abs(move.y)
                            if (travelled > slop && !held) swiping = true
                            if (swiping) {
                                change.consume()
                                accX += move.x
                                accY += move.y
                                while (max(abs(accX), abs(accY)) >= step) {
                                    tick()
                                    val key =
                                        if (abs(accX) > abs(accY)) {
                                            val direction = if (accX > 0) WheelKey.RIGHT else WheelKey.LEFT
                                            accX -= if (accX > 0) step else -step
                                            direction
                                        } else {
                                            val direction = if (accY > 0) WheelKey.DOWN else WheelKey.UP
                                            accY -= if (accY > 0) step else -step
                                            direction
                                        }
                                    send(key.command)
                                }
                            }
                        }
                    }
                },
    ) {
        Arrow(Icons.Default.KeyboardArrowUp, R.string.remote_up, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        Arrow(Icons.Default.KeyboardArrowDown, R.string.remote_down, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        Arrow(Icons.Default.KeyboardArrowLeft, R.string.remote_left, Modifier.align(Alignment.CenterStart).padding(start = 12.dp))
        Arrow(Icons.Default.KeyboardArrowRight, R.string.remote_right, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
        OkKey(send, okSize, Modifier.align(Alignment.Center))
    }
}

/** The direction the touch is furthest along from the centre. */
private fun wheelKeyAt(fromCentre: Offset): WheelKey =
    when {
        abs(fromCentre.x) > abs(fromCentre.y) -> if (fromCentre.x > 0) WheelKey.RIGHT else WheelKey.LEFT
        else -> if (fromCentre.y > 0) WheelKey.DOWN else WheelKey.UP
    }

/** A direction mark: still, so the wheel does not flicker under a moving finger. */
@Composable
private fun Arrow(
    icon: ImageVector,
    description: Int,
    modifier: Modifier,
) {
    Icon(
        icon,
        contentDescription = stringResource(description),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(40.dp),
    )
}

/** OK is a standard filled icon button: the scalloped disc relaxes to a plain circle under the finger. */
@Composable
private fun OkKey(
    send: (String) -> Unit,
    size: Dp,
    modifier: Modifier,
) {
    val tick = rememberKeyTick()
    FilledIconButton(
        onClick = {
            tick()
            send("key:ok")
        },
        modifier = modifier.size(size),
        shapes = IconButtonDefaults.shapes(shape = MaterialShapes.Cookie9Sided.toShape(), pressedShape = CircleShape),
    ) {
        Text(stringResource(R.string.remote_ok), style = MaterialTheme.typography.titleLarge)
    }
}
