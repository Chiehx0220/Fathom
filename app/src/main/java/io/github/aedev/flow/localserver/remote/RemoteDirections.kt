@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.LocalHttpServer
import kotlin.math.abs
import kotlin.math.max

// Finger travel (dp) on the direction wheel that counts as one press.
private const val WHEEL_STEP_DP = 40f

/** Browsing: the source switch, a search box, the direction wheel and the shortcuts to the page's main places. */
@Composable
internal fun DirectionsMode(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    val tick = rememberKeyTick()
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.services.size > 1) {
            ServiceSwitch(state.services, state.activeService) { send("service:$it") }
        }
        SearchField(send)
        // The wheel takes what room is left, up to a comfortable size, so a small phone does not push the shortcut keys off screen.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            DirectionWheel(send, minOf(280.dp, maxHeight, maxWidth))
            // Menu, not MoreVert: matches the web key-hints overlay's glyph for "key:menu".
            FilledTonalIconButton(
                onClick = { tick(); send("key:menu") },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.TopEnd).size(52.dp),
            ) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.remote_options))
            }
        }
        ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
            shortcutKey(Icons.AutoMirrored.Filled.ArrowBack, R.string.remote_back) { send("back") }
            shortcutKey(Icons.Default.Home, R.string.remote_home) { send("goto:home") }
            shortcutKey(Icons.Default.Subscriptions, R.string.remote_subscriptions) { send("goto:subscriptions") }
            shortcutKey(Icons.Default.History, R.string.remote_history) { send("goto:history") }
        }
    }
}

// Typing on a screen with no keyboard is the worst part of a remote: type here, and the page runs the search.
@Composable
private fun SearchField(send: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = { if (text.isNotBlank()) send("search:${text.trim()}") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.remote_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { text = "" }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remote_search_clear))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
    )
}

// The page's own source switcher (YouTube / Bilibili / ...), mirrored: the one it is on is lit, a tap moves the page there.
@Composable
private fun ServiceSwitch(
    services: List<Pair<Int, String>>,
    active: Int?,
    onSelect: (Int) -> Unit,
) {
    ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
        services.forEach { (id, name) ->
            customItem({
                val interaction = remember { MutableInteractionSource() }
                val tick = rememberKeyTick()
                ToggleButton(
                    checked = id == active,
                    onCheckedChange = { tick(); onSelect(id) },
                    shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
                    interactionSource = interaction,
                    modifier = Modifier.weight(1f).animateWidth(interaction),
                ) {
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }) {}
        }
    }
}

@Composable
private fun DirectionWheel(
    send: (String) -> Unit,
    wheelSize: Dp,
) {
    val okSize = wheelSize * 0.4f
    val haptic = LocalHapticFeedback.current
    Box(
        modifier =
            Modifier
                .size(wheelSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(Unit) {
                    // A tap presses whichever part of the wheel was touched: the middle is OK, the rest is the nearest direction.
                    // A long press anywhere on the wheel opens the selected card's options menu.
                    detectTapGestures(onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); send("key:menu") }) { at ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val delta = at - centre
                        if (delta.getDistance() <= okSize.toPx() / 2f) {
                            send("key:ok")
                        } else if (abs(delta.x) > abs(delta.y)) {
                            send(if (delta.x > 0) "key:right" else "key:left")
                        } else {
                            send(if (delta.y > 0) "key:down" else "key:up")
                        }
                    }
                }.pointerInput(Unit) {
                    // A swipe presses the direction it points, once per step of travel, so a long swipe keeps moving.
                    val step = WHEEL_STEP_DP.dp.toPx()
                    var accX = 0f
                    var accY = 0f
                    detectDragGestures(
                        onDragStart = {
                            accX = 0f
                            accY = 0f
                        },
                    ) { change, drag ->
                        change.consume()
                        accX += drag.x
                        accY += drag.y
                        while (max(abs(accX), abs(accY)) >= step) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (abs(accX) > abs(accY)) {
                                send(if (accX > 0) "key:right" else "key:left")
                                accX -= if (accX > 0) step else -step
                            } else {
                                send(if (accY > 0) "key:down" else "key:up")
                                accY -= if (accY > 0) step else -step
                            }
                        }
                    }
                },
    ) {
        val arrowTint = MaterialTheme.colorScheme.onSurfaceVariant
        val arrow = Modifier.size(40.dp)
        Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.remote_up), arrow.align(Alignment.TopCenter).padding(top = 12.dp), arrowTint)
        Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.remote_down), arrow.align(Alignment.BottomCenter).padding(bottom = 12.dp), arrowTint)
        Icon(Icons.Default.KeyboardArrowLeft, stringResource(R.string.remote_left), arrow.align(Alignment.CenterStart).padding(start = 12.dp), arrowTint)
        Icon(Icons.Default.KeyboardArrowRight, stringResource(R.string.remote_right), arrow.align(Alignment.CenterEnd).padding(end = 12.dp), arrowTint)
        Box(
            Modifier.size(okSize).align(Alignment.Center).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.remote_ok), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
