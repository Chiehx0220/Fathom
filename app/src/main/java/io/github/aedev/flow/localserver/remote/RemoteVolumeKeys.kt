package io.github.aedev.flow.localserver.remote

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.localserver.RemoteState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Takes the phone's volume keys while the remote is on screen: [onStep] gets the change for each press and the system never sees the key. */
internal fun Modifier.remoteVolumeKeys(onStep: (Float) -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val delta =
            when (event.key) {
                Key.VolumeUp -> VOLUME_STEP
                Key.VolumeDown -> -VOLUME_STEP
                else -> return@onPreviewKeyEvent false
            }
        if (event.type == KeyEventType.KeyDown) onStep(delta)
        true
    }

/** Whether the volume indicator shows; every key press shows it for a moment and a new press starts the moment over. */
@Stable
internal class VolumeHudState {
    var shown by mutableStateOf(false)
        private set
    internal var presses by mutableIntStateOf(0)
        private set

    fun press() {
        presses++
    }

    suspend fun run() {
        if (presses == 0) return
        shown = true
        delay(HUD_MILLIS)
        shown = false
    }
}

private const val HUD_MILLIS = 1500L

@Composable
internal fun rememberVolumeHud(): VolumeHudState {
    val hud = remember { VolumeHudState() }
    LaunchedEffect(hud.presses) { hud.run() }
    return hud
}

/** The page's volume as it is now; the page reports it back within a tenth of a second of a key press. */
@Composable
internal fun VolumeHud(
    hud: VolumeHudState,
    state: RemoteState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(hud.shown, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.inverseSurface, tonalElevation = 6.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                val level = if (state.muted) 0f else state.volume
                Icon(
                    when {
                        level == 0f -> Icons.Default.VolumeOff
                        level < 0.5f -> Icons.Default.VolumeDown
                        else -> Icons.Default.VolumeUp
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                )
                LinearProgressIndicator(
                    progress = { level },
                    modifier = Modifier.padding(horizontal = 14.dp).width(160.dp),
                    color = MaterialTheme.colorScheme.inversePrimary,
                    trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f),
                )
                Text(
                    "${(level * 100).roundToInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}
