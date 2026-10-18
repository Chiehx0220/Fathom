@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.localserver.RemoteState

/** Session-level controls above the mode pages: source menu (paired, 2+ sources) and Deep Flow. Outside the page transition. */
@Composable
internal fun RemoteStatusStrip(
    state: RemoteState,
    connected: Boolean,
    send: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (connected && state.services.size > 1) {
            SourceChip(state.services, state.activeService) { send("service:$it") }
        }
        Spacer(Modifier.weight(1f))
        DeepFlowToggle()
    }
}

// Source selector; mirrors the web page's source chip.
@Composable
private fun SourceChip(
    services: List<Pair<Int, String>>,
    active: Int?,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tick = rememberKeyTick()
    val current = services.firstOrNull { it.first == active } ?: services.first()
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(current.second, maxLines = 1) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            border = null,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            services.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    trailingIcon = if (id == current.first) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
                    onClick = {
                        open = false
                        tick()
                        onSelect(id)
                    },
                )
            }
        }
    }
}
