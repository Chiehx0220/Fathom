@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import io.github.aedev.flow.localserver.ServerService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.LocalHttpServer

private enum class RemoteMode(val label: Int, val icon: ImageVector) {
    DIRECTIONS(R.string.remote_tab_directions, Icons.Default.RadioButtonChecked),
    PLAYBACK(R.string.remote_tab_playback, Icons.Default.PlayArrow),
    TOUCHPAD(R.string.remote_tab_touchpad, Icons.Default.TouchApp),
}

/** The remote: a top bar with the connection, the page for the chosen way of driving, and the switch between them. */
@Composable
internal fun RemoteScreen(onBack: () -> Unit) {
    val lock by LocalHttpServer.remoteLock.collectAsState()
    val state by LocalHttpServer.remoteState.collectAsState()
    val send = remember { { command: String -> LocalHttpServer.addPendingCommand(command) } }
    var mode by rememberSaveable { mutableStateOf(RemoteMode.DIRECTIONS) }

    // Follow the page: a video opening jumps to playback keys, leaving it goes back to browsing. Tapping a tab still wins until the page changes again.
    LaunchedEffect(lock.locked, state.watching) {
        if (lock.locked) mode = if (state.watching) RemoteMode.PLAYBACK else RemoteMode.DIRECTIONS
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.remote_title))
                        Text(
                            if (lock.locked) stringResource(R.string.remote_connected_to, lock.clientIp ?: "") else stringResource(R.string.remote_not_connected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.remote_back))
                    }
                },
                actions = {
                    if (lock.locked) {
                        IconButton(onClick = { LocalHttpServer.releaseLock() }) {
                            Icon(Icons.Default.LinkOff, contentDescription = stringResource(R.string.remote_disconnect))
                        }
                    }
                },
            )
        },
        bottomBar = {
            ShortNavigationBar {
                RemoteMode.entries.forEach { entry ->
                    ShortNavigationBarItem(
                        selected = mode == entry,
                        onClick = { mode = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!lock.locked) {
                NotConnected()
            } else {
                when (mode) {
                    RemoteMode.DIRECTIONS -> DirectionsMode(state, send)
                    RemoteMode.PLAYBACK -> PlaybackMode(state, send)
                    RemoteMode.TOUCHPAD -> TouchpadMode(state, send)
                }
            }
        }
    }
}

/** Nothing paired yet: says what to do, and shows the address to open so it does not have to be looked up. */
@Composable
private fun NotConnected() {
    val serverRunning by ServerService.runningState.collectAsState()
    val address =
        remember(serverRunning) {
            if (serverRunning) ServerService.getLocalIpAddress()?.let { "http://$it:${ServerService.PORT}" } else null
        }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.remote_address_copied)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.LinkOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.remote_not_connected), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            ConnectStep(1, stringResource(R.string.remote_connect_step_open))
            if (address != null) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(address, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(address))
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.remote_copy_address))
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.remote_server_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ConnectStep(2, stringResource(R.string.remote_connect_step_cast))
        }
    }
}

@Composable
private fun ConnectStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
