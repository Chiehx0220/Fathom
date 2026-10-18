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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
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
import io.github.aedev.flow.localserver.RemoteState

/**
 * Directions page: search, subscriptions/library, direction wheel, shortcut keys.
 */
@Composable
internal fun DirectionsMode(
    state: RemoteState,
    send: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(RemotePagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(RemoteGroupSpacing),
    ) {
        SearchField(send)
        ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
            wideKey(Icons.Default.Subscriptions, R.string.remote_subscriptions) { send("goto:subscriptions") }
            wideKey(Icons.Default.VideoLibrary, R.string.library) { send("goto:library") }
        }
        // Wheel takes remaining room up to a comfortable size (small phone: keys stay on screen).
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            DirectionWheel(send, minOf(300.dp, maxHeight, maxWidth))
        }
        ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
            shortcutKey(Icons.AutoMirrored.Filled.ArrowBack, R.string.remote_back) { send("back") }
            shortcutKey(Icons.Default.Home, R.string.remote_home) { send("goto:home") }
            // Menu, not MoreVert: web key-hints overlay glyph for "key:menu".
            shortcutKey(Icons.Default.Menu, R.string.remote_options) { send("key:menu") }
        }
    }
}
