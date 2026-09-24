@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.LocalHttpServer

/**
 * Browsing, top to bottom in order of how rarely it is reached for: the source switch, the search, the two places
 * that are seldom needed, the direction wheel, and under the thumb the keys used all the time.
 */
@Composable
internal fun DirectionsMode(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.services.size > 1) {
            ServiceSwitch(state.services, state.activeService) { send("service:$it") }
        }
        SearchField(send)
        ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
            wideKey(Icons.Default.Subscriptions, R.string.remote_subscriptions) { send("goto:subscriptions") }
            wideKey(Icons.Default.History, R.string.remote_history) { send("goto:history") }
        }
        // The wheel takes what room is left, up to a comfortable size, so a small phone does not push the keys below off screen.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            DirectionWheel(send, minOf(300.dp, maxHeight, maxWidth))
        }
        ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
            shortcutKey(Icons.AutoMirrored.Filled.ArrowBack, R.string.remote_back) { send("back") }
            shortcutKey(Icons.Default.Home, R.string.remote_home) { send("goto:home") }
            // Menu, not MoreVert: matches the web key-hints overlay's glyph for "key:menu".
            shortcutKey(Icons.Default.Menu, R.string.remote_options) { send("key:menu") }
        }
    }
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
