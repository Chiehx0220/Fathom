@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.LocalHttpServer

// Pointer travel per pixel of finger travel, page scroll per pixel, and how often (ms) travel is sent as one command.
private const val POINTER_SPEED = 2.2f
private const val SCROLL_SPEED = 3f
private const val SEND_INTERVAL_MS = 24L

/** A touchpad for anything small on the page, a strip to scroll it, and the two keys still needed. */
@Composable
internal fun TouchpadMode(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Touchpad(send, Modifier.weight(1f).fillMaxHeight())
            ScrollStrip(send, Modifier.width(56.dp).fillMaxHeight())
        }
        ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
            wideKey(Icons.AutoMirrored.Filled.ArrowBack, R.string.remote_back) { send("back") }
            wideKey(
                if (state.watching && !state.paused) Icons.Default.Pause else Icons.Default.PlayArrow,
                R.string.remote_play_pause,
            ) { send("play_pause") }
        }
    }
}

@Composable
private fun Touchpad(send: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(Unit) {
                    var pendingX = 0f
                    var pendingY = 0f
                    var lastSent = 0L
                    detectDragGestures { change, drag ->
                        change.consume()
                        pendingX += drag.x * POINTER_SPEED
                        pendingY += drag.y * POINTER_SPEED
                        val now = System.currentTimeMillis()
                        if (now - lastSent >= SEND_INTERVAL_MS) {
                            send("pointer_move:${pendingX.toInt()},${pendingY.toInt()}")
                            pendingX = 0f
                            pendingY = 0f
                            lastSent = now
                        }
                    }
                }.pointerInput(Unit) {
                    detectTapGestures(onTap = { send("pointer_click") })
                },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.remote_touchpad_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScrollStrip(send: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(Unit) {
                    var pending = 0f
                    var lastSent = 0L
                    detectDragGestures { change, drag ->
                        change.consume()
                        // Dragging up moves the page up under the finger, like a phone: that is scrolling down.
                        pending -= drag.y * SCROLL_SPEED
                        val now = System.currentTimeMillis()
                        if (now - lastSent >= SEND_INTERVAL_MS) {
                            send("pointer_scroll:${pending.toInt()}")
                            pending = 0f
                            lastSent = now
                        }
                    }
                },
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.remote_scroll), modifier = Modifier.padding(top = 12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.width(4.dp).weight(1f).padding(vertical = 8.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant))
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(bottom = 12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
