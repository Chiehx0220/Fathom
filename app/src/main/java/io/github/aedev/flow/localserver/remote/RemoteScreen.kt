@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.RemoteLock
import io.github.aedev.flow.localserver.RemoteSession
import io.github.aedev.flow.localserver.RemoteState

private enum class RemoteMode(
    val label: Int,
    val icon: ImageVector,
) {
    DIRECTIONS(R.string.remote_tab_directions, Icons.Default.RadioButtonChecked),
    PLAYBACK(R.string.remote_tab_playback, Icons.Default.PlayArrow),
    TOUCHPAD(R.string.remote_tab_touchpad, Icons.Default.TouchApp),
}

/** Remote screen: connection top bar, mode pages, mode switch. */
@Composable
internal fun RemoteScreen(onBack: () -> Unit) {
    val lock by RemoteSession.remoteLock.collectAsState()
    val state by RemoteSession.remoteState.collectAsState()
    var mode by rememberSaveable { mutableStateOf(RemoteMode.DIRECTIONS) }
    val volumeHud = rememberVolumeHud()
    val keysFocus = remember { FocusRequester() }
    LaunchedEffect(lock.locked) { if (lock.locked) keysFocus.requestFocus() }

    // Follows page state (video open → playback); manual tab choice holds until it changes again.
    LaunchedEffect(lock.locked, state.watching) {
        if (lock.locked) mode = if (state.watching) RemoteMode.PLAYBACK else RemoteMode.DIRECTIONS
    }

    Scaffold(
        modifier =
            Modifier
                .focusRequester(keysFocus)
                .focusable()
                .remoteVolumeKeys { delta ->
                    if (lock.locked) {
                        sendRemoteCommand(volumeCommand(delta))
                        volumeHud.press()
                    }
                },
        topBar = { RemoteTopBar(lock, onBack) },
        bottomBar = {
            // Modes require a paired page.
            if (lock.locked) RemoteModeBar(mode) { mode = it }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RemoteStatusStrip(state, connected = lock.locked, send = ::sendRemoteCommand)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                VolumeHud(volumeHud, state, Modifier.align(BiasAlignment(0f, -0.72f)).zIndex(1f))
                RemotePages(page = if (lock.locked) mode else null, state = state)
            }
        }
    }
}

@Composable
private fun RemoteTopBar(
    lock: RemoteLock,
    onBack: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        title = {
            Column {
                Text(stringResource(R.string.remote_title))
                Text(
                    if (lock.locked) {
                        stringResource(R.string.remote_connected_to, lock.clientIp ?: "")
                    } else {
                        stringResource(R.string.remote_not_connected)
                    },
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
                IconButton(onClick = { RemoteSession.releaseLock() }) {
                    Icon(Icons.Default.LinkOff, contentDescription = stringResource(R.string.remote_disconnect))
                }
            }
        },
    )
}

@Composable
private fun RemoteModeBar(
    mode: RemoteMode,
    onSelect: (RemoteMode) -> Unit,
) {
    ShortNavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        RemoteMode.entries.forEach { entry ->
            ShortNavigationBarItem(
                selected = mode == entry,
                onClick = { onSelect(entry) },
                icon = { Icon(entry.icon, contentDescription = null) },
                label = { Text(stringResource(entry.label)) },
            )
        }
    }
}

/** [page] null = not connected. */
@Composable
private fun RemotePages(
    page: RemoteMode?,
    state: RemoteState,
) {
    val motion = MaterialTheme.motionScheme
    // Slide direction = tab order.
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val direction = if ((targetState?.ordinal ?: -1) > (initialState?.ordinal ?: -1)) 1 else -1
            (
                slideInHorizontally(motion.fastSpatialSpec<IntOffset>()) { it / 5 * direction } +
                    fadeIn(motion.defaultEffectsSpec<Float>())
            ) togetherWith
                (
                    slideOutHorizontally(motion.fastSpatialSpec<IntOffset>()) { -it / 5 * direction } +
                        fadeOut(motion.defaultEffectsSpec<Float>())
                )
        },
        label = "remotePage",
    ) {
        when (it) {
            null -> NotConnected()
            RemoteMode.DIRECTIONS -> DirectionsMode(state, ::sendRemoteCommand)
            RemoteMode.PLAYBACK -> PlaybackMode(state, ::sendRemoteCommand)
            RemoteMode.TOUCHPAD -> TouchpadMode(state, ::sendRemoteCommand)
        }
    }
}
