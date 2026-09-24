@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.LocalHttpServer

/**
 * A playing video, top to bottom: what it is, then the seek bar and transport keys in the middle where the thumb
 * rests, then the volume and screen switches, and the way back to the page last.
 */
@Composable
internal fun PlaybackMode(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NowPlaying(state, send)

        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
            SeekBar(state, send)
            TransportKeys(state, send)
        }

        ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
            toolKey(Icons.Default.VolumeDown, R.string.remote_volume_down) { send("volume:-$VOLUME_STEP") }
            toolKey(Icons.Default.VolumeUp, R.string.remote_volume_up) { send("volume:$VOLUME_STEP") }
            // Lit while muted, so it reads as a switch and not as a second volume key.
            toolKey(Icons.Default.VolumeOff, R.string.remote_mute, toggled = state.muted) { send("mute") }
            toolKey(
                if (state.fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                R.string.remote_fullscreen,
                toggled = state.fullscreen,
            ) { send("fullscreen") }
        }
        ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
            wideKey(Icons.AutoMirrored.Filled.ArrowBack, R.string.remote_back) { send("back") }
        }
    }
}

// One row, one height: chapter keys only when the video has chapters, and play the widest.
@Composable
private fun TransportKeys(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    val hasChapters = state.chapters.isNotEmpty()
    ButtonGroup(overflowIndicator = {}, modifier = Modifier.fillMaxWidth()) {
        if (hasChapters) {
            transportKey(Icons.Default.SkipPrevious, R.string.remote_chapter_previous, weight = 0.8f, iconSize = 24.dp, strong = true) { send("chapter:prev") }
        }
        transportKey(Icons.Default.Replay10, R.string.remote_rewind, weight = 1f, iconSize = 28.dp) { send("rewind") }
        transportKey(
            if (state.hasVideo && !state.paused) Icons.Default.Pause else Icons.Default.PlayArrow,
            R.string.remote_play_pause,
            weight = 1.8f,
            iconSize = 44.dp,
            strong = true,
        ) { send("play_pause") }
        transportKey(Icons.Default.Forward10, R.string.remote_forward, weight = 1f, iconSize = 28.dp) { send("forward") }
        if (hasChapters) {
            transportKey(Icons.Default.SkipNext, R.string.remote_chapter_next, weight = 0.8f, iconSize = 24.dp, strong = true) { send("chapter:next") }
        }
    }
}

// The seek bar: a wavy line that ripples while the video plays and goes flat when it pauses. While the thumb is held it
// shows the finger, not the (still moving) page; the jump is sent on release.
@Composable
private fun SeekBar(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = state.durationSec.toFloat()
    val position = if (dragging) dragValue else state.positionSec.toFloat()
    val fraction = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
    val motion = MaterialTheme.motionScheme
    val wave by animateFloatAsState(if (state.hasVideo && !state.paused && !dragging) 1f else 0f, motion.defaultEffectsSpec(), label = "seekWave")
    val thumbWidth by animateFloatAsState(if (dragging) 2f else 4f, motion.fastSpatialSpec(), label = "seekThumb")
    fun fractionAt(x: Float, width: Int) = if (width > 0) (x / width).coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(duration) {
                    if (duration > 0f) detectTapGestures { send("seek_to:${fractionAt(it.x, size.width) * duration}") }
                }.pointerInput(duration) {
                    if (duration <= 0f) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragValue = fractionAt(it.x, size.width) * duration
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragValue = fractionAt(change.position.x, size.width) * duration
                        },
                        onDragEnd = {
                            send("seek_to:$dragValue")
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            LinearWavyProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                amplitude = { wave },
            )
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(fraction.coerceAtLeast(0.001f)))
                Box(Modifier.size(thumbWidth.dp, 28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.weight((1f - fraction).coerceAtLeast(0.001f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NowPlaying(state: LocalHttpServer.RemoteState, send: (String) -> Unit) {
    var showChapters by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!state.hasVideo) {
            Text(stringResource(R.string.remote_no_video), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        Text(stringResource(R.string.remote_now_playing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state.title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        // In the mini player the video is off its page: one key brings the page back.
        if (state.minimized) {
            FilledTonalButton(onClick = { send("expand") }, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.remote_open_video_page))
            }
        }
        state.chapters.getOrNull(state.chapterIndex)?.let { chapterTitle ->
            FilledTonalButton(
                onClick = { showChapters = true },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(top = 2.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(chapterTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.remote_chapter_list),
                    modifier = Modifier.size(20.dp),
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
            FilledTonalButton(onClick = { send("skip") }, shapes = ButtonDefaults.shapes(), modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.remote_skip_segment, label))
            }
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
                    ListItem(
                        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = if (active) { { Icon(Icons.Default.PlayArrow, contentDescription = null) } } else null,
                        colors =
                            ListItemDefaults.colors(
                                containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                headlineColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { onJump(index) },
                    )
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
