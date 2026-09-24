@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
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
            val motion = MaterialTheme.motionScheme
            // Pages slide sideways the way the tabs are laid out: to the right for a tab further right, to the left for one further left.
            AnimatedContent(
                targetState = if (lock.locked) mode else null,
                transitionSpec = {
                    val direction = if ((targetState?.ordinal ?: -1) > (initialState?.ordinal ?: -1)) 1 else -1
                    (slideInHorizontally(motion.fastSpatialSpec<IntOffset>()) { it / 5 * direction } + fadeIn(motion.defaultEffectsSpec<Float>())) togetherWith
                        (slideOutHorizontally(motion.fastSpatialSpec<IntOffset>()) { -it / 5 * direction } + fadeOut(motion.defaultEffectsSpec<Float>()))
                },
                label = "remotePage",
            ) { page ->
                when (page) {
                    null -> NotConnected()
                    RemoteMode.DIRECTIONS -> DirectionsMode(state, ::sendRemoteCommand)
                    RemoteMode.PLAYBACK -> PlaybackMode(state, ::sendRemoteCommand)
                    RemoteMode.TOUCHPAD -> TouchpadMode(state, ::sendRemoteCommand)
                }
            }
        }
    }
}
