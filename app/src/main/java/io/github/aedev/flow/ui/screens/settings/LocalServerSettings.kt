package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.localserver.RemoteActivity
import io.github.aedev.flow.localserver.ServerService
import io.github.aedev.flow.ui.components.shared.flowArtistShape
import kotlinx.coroutines.launch

private val CardPadding = 16.dp
private val CardSpacing = 16.dp
private val EmblemSize = 56.dp

/**
 * The local server card of the settings list, sitting beside the taste card in the same Material 3 Expressive form.
 * It is kept out of the upstream settings files so their edits do not collide with it, and it follows the service's
 * actual state rather than the saved on/off preference.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LocalServerSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { PlayerPreferences(context) }
    val running by ServerService.runningState.collectAsState()
    val address =
        remember(running) {
            if (running) ServerService.getLocalIpAddress()?.let { "http://$it:${ServerService.PORT}" } else null
        }
    val openRemote = { context.startActivity(android.content.Intent(context, RemoteActivity::class.java)) }

    Surface(
        onClick = openRemote,
        enabled = running,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(CardPadding),
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(EmblemSize),
                shape = flowArtistShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "📡", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text =
                        stringResource(
                            if (running) R.string.settings_local_server_status_online else R.string.settings_local_server_status_offline,
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_item_local_server),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = address ?: stringResource(R.string.settings_local_server_hint_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = running,
                onCheckedChange = { enabled ->
                    scope.launch { preferences.setLocalServerEnabled(enabled) }
                    if (enabled) ServerService.start(context) else ServerService.stop(context)
                },
            )
        }
    }
}
