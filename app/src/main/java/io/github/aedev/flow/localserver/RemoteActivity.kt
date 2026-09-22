package io.github.aedev.flow.localserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.ui.theme.FlowTheme
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import kotlin.math.abs
import kotlin.math.max

/**
 * The phone as a remote for the page that paired with the local server (its cast button). Three ways to drive it:
 * a direction pad for browsing, playback keys for a playing video, and a touchpad for anything small. Commands go
 * straight to the server, which hands them to the paired page; the page reports back how it is playing.
 */
class RemoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataManager = LocalDataManager(applicationContext)
        setContent {
            val themeMode by dataManager.themeMode.collectAsState(initial = ThemeMode.MATERIAL_YOU)
            val themeVariant by dataManager.themeVariant.collectAsState(initial = ThemeVariant.DARK)
            FlowTheme(themeMode = themeMode, themeVariant = themeVariant) {
                RemoteScreen(onBack = { finish() })
            }
        }
    }
}

private enum class RemoteMode(val label: Int, val icon: ImageVector) {
    DIRECTIONS(R.string.remote_tab_directions, Icons.Default.RadioButtonChecked),
    PLAYBACK(R.string.remote_tab_playback, Icons.Default.PlayArrow),
    TOUCHPAD(R.string.remote_tab_touchpad, Icons.Default.TouchApp),
}

// Pointer travel per pixel of finger travel, page scroll per pixel, and how often (ms) travel is sent as one command.
private const val POINTER_SPEED = 2.2f
private const val SCROLL_SPEED = 3f
private const val SEND_INTERVAL_MS = 24L

// Finger travel (dp) on the direction wheel that counts as one press.
private const val WHEEL_STEP_DP = 40f
private const val VOLUME_STEP = 0.1f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteScreen(onBack: () -> Unit) {
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
            NavigationBar {
                RemoteMode.entries.forEach { entry ->
                    NavigationBarItem(
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

@Composable
private fun NotConnected() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.remote_how_to_connect), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

// ---- Directions -----------------------------------------------------------------------------------------------

@Composable
private fun DirectionsMode(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.services.size > 1) {
            ServiceSwitch(state.services, state.activeService) { send("service:$it") }
        }
        SearchField(send)
        // The wheel takes what room is left, up to a comfortable size, so a small phone does not push the shortcut keys off screen.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            DirectionWheel(send, minOf(280.dp, maxHeight, maxWidth))
            // Menu, not MoreVert: matches the web key-hints overlay's glyph for "key:menu".
            FilledTonalIconButton(onClick = { send("key:menu") }, modifier = Modifier.align(Alignment.TopEnd).size(52.dp)) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.remote_options))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShortcutKey(Icons.AutoMirrored.Filled.ArrowBack, R.string.remote_back, Modifier.weight(1f)) { send("back") }
            ShortcutKey(Icons.Default.Home, R.string.remote_home, Modifier.weight(1f)) { send("goto:home") }
            ShortcutKey(Icons.Default.Subscriptions, R.string.remote_subscriptions, Modifier.weight(1f)) { send("goto:subscriptions") }
            ShortcutKey(Icons.Default.History, R.string.remote_history, Modifier.weight(1f)) { send("goto:history") }
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        services.forEach { (id, name) ->
            val selected = id == active
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

// Icon over a short label, so four of them fit in a row.
@Composable
private fun ShortcutKey(
    icon: ImageVector,
    label: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(60.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DirectionWheel(
    send: (String) -> Unit,
    wheelSize: Dp,
) {
    val okSize = wheelSize * 0.4f
    Box(
        modifier =
            Modifier
                .size(wheelSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(Unit) {
                    // A tap presses whichever part of the wheel was touched: the middle is OK, the rest is the nearest direction.
                    // A long press anywhere on the wheel opens the selected card's options menu.
                    detectTapGestures(onLongPress = { send("key:menu") }) { at ->
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

// ---- Playback -------------------------------------------------------------------------------------------------

@Composable
private fun PlaybackMode(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NowPlaying(state, send)

        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.chapters.isNotEmpty()) {
                FilledIconButton(onClick = { send("chapter:prev") }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.remote_chapter_previous))
                }
            }
            FilledTonalIconButton(onClick = { send("rewind") }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.remote_rewind), modifier = Modifier.size(26.dp))
            }
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { send("play_pause") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.watching && !state.paused) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.remote_play_pause),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            FilledTonalIconButton(onClick = { send("forward") }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.remote_forward), modifier = Modifier.size(26.dp))
            }
            if (state.chapters.isNotEmpty()) {
                FilledIconButton(onClick = { send("chapter:next") }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.remote_chapter_next))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton(Icons.Default.VolumeDown, R.string.remote_volume_down, Modifier.weight(1f)) { send("volume:-$VOLUME_STEP") }
            ToolButton(Icons.Default.VolumeUp, R.string.remote_volume_up, Modifier.weight(1f)) { send("volume:$VOLUME_STEP") }
            // Lit while muted, so it reads as a switch and not as a second volume key.
            ToolButton(Icons.Default.VolumeOff, R.string.remote_mute, Modifier.weight(1f), active = state.muted) { send("mute") }
            ToolButton(
                if (state.fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                R.string.remote_fullscreen,
                Modifier.weight(1f),
                active = state.fullscreen,
            ) { send("fullscreen") }
            ToolButton(Icons.AutoMirrored.Filled.ArrowBack, R.string.remote_back, Modifier.weight(1f)) { send("back") }
        }
    }
}

@Composable
private fun NowPlaying(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    var showChapters by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!state.watching) {
            Text(stringResource(R.string.remote_no_video), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        Text(stringResource(R.string.remote_now_playing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state.title.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        state.chapters.getOrNull(state.chapterIndex)?.let { chapterTitle ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { showChapters = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(chapterTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(
                    Icons.Default.List,
                    contentDescription = stringResource(R.string.remote_chapter_list),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (showChapters) {
            ChapterListDialog(
                chapters = state.chapters,
                activeIndex = state.chapterIndex,
                onDismiss = { showChapters = false },
                onJump = { index -> send("chapter:jump:$index"); showChapters = false },
            )
        }
        // Mirrors watch.js's skip button: visible only while inside a SponsorBlock segment.
        state.skipLabel?.let { label ->
            FilledTonalButton(onClick = { send("skip") }, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.remote_skip_segment, label))
            }
        }

        // While the thumb is held the slider shows the finger, not the (still moving) page; the jump is sent on release.
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        val duration = state.durationSec.toFloat()
        val position = if (dragging) dragValue else state.positionSec.toFloat()
        Slider(
            value = if (duration > 0f) position.coerceIn(0f, duration) else 0f,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                send("seek_to:$dragValue")
                dragging = false
            },
            valueRange = 0f..max(duration, 1f),
            enabled = duration > 0f,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChapterListDialog(
    chapters: List<String>,
    activeIndex: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_chapters)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(chapters) { index, title ->
                    val active = index == activeIndex
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { onJump(index) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (active) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ---- Touchpad -------------------------------------------------------------------------------------------------

@Composable
private fun TouchpadMode(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Touchpad(send, Modifier.weight(1f).fillMaxHeight())
            ScrollStrip(send, Modifier.width(56.dp).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyButton(Icons.AutoMirrored.Filled.ArrowBack, R.string.remote_back, Modifier.weight(1f)) { send("back") }
            KeyButton(
                if (state.watching && !state.paused) Icons.Default.Pause else Icons.Default.PlayArrow,
                R.string.remote_play_pause,
                Modifier.weight(1f),
            ) { send("play_pause") }
        }
    }
}

@Composable
private fun Touchpad(send: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
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

// ---- Shared buttons -------------------------------------------------------------------------------------------

@Composable
private fun KeyButton(
    icon: ImageVector,
    description: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(52.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(description))
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    description: Int,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val colors =
        if (active) {
            IconButtonDefaults.filledIconButtonColors()
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        }
    FilledIconButton(onClick = onClick, modifier = modifier.height(52.dp), colors = colors) {
        Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(24.dp))
    }
}
