package io.github.aedev.flow.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// The local server card of the settings list, kept out of the upstream settings files so their edits
// do not collide with it. It follows the service's actual state, not the saved on/off preference.
@Composable
internal fun LocalServerSettingsSection() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }
    val running by io.github.aedev.flow.localserver.ServerService.runningState.collectAsState()
    Column {
    run {
        Text(
            text = stringResource(R.string.settings_header_local_server),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 8.dp),
        )
    }
    run {
        val localServerAddress =
            remember(running) {
                if (running) {
                    io.github.aedev.flow.localserver.ServerService.getLocalIpAddress()?.let { ip ->
                        "http://$ip:${io.github.aedev.flow.localserver.ServerService.PORT}"
                    }
                } else {
                    null
                }
            }
        val openRemote: () -> Unit = {
            context.startActivity(android.content.Intent(context, io.github.aedev.flow.localserver.RemoteActivity::class.java))
        }
        // "Smoky" here is a finish, not a fixed hue: take the ACTIVE theme's own
        // primary/primaryContainer (the same pair Flow Engine's card uses) and mute
        // them - lower saturation, slightly darker. Default theme (red) reads as smoky
        // red/dusty rose; a green theme reads as smoky green, etc. - always the current
        // theme's color family, just with a dustier finish than Flow Engine's card.
        val themePrimary = MaterialTheme.colorScheme.primary
        val themePrimaryContainer = MaterialTheme.colorScheme.primaryContainer
        val localServerAccentDark = remember(themePrimary) { smokyVariant(themePrimary) }
        val localServerAccentLight = remember(themePrimaryContainer) { smokyVariant(themePrimaryContainer) }
        val onLocalServerAccent = Color.White

        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(enabled = running, onClick = openRemote),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
            ) {
                // 1. Background Layer (Gradient)
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(
                                brush =
                                    Brush.linearGradient(
                                        colors = listOf(localServerAccentDark, localServerAccentLight),
                                    ),
                            ),
                )
                // 2. Background Decor (Abstract Shapes)
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        radius = size.width * 0.5f,
                        center = Offset(size.width, 0f),
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.05f),
                        radius = size.width * 0.3f,
                        center = Offset(0f, size.height),
                    )
                }

                // 3. Huge Emoji Icon (Watermark style, matching the Flow persona card)
                Text(
                    text = "📡", // 📡
                    fontSize = 120.sp,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 20.dp, y = 20.dp)
                            .alpha(0.15f),
                )

                // 4. Main Content
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Status Badge
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(6.dp)
                                            .background(
                                                color =
                                                    if (running) {
                                                        Color(0xFF4CAF50)
                                                    } else {
                                                        onLocalServerAccent.copy(alpha = 0.5f)
                                                    },
                                                shape = CircleShape,
                                            ),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text =
                                        if (running) {
                                            stringResource(R.string.settings_local_server_status_online)
                                        } else {
                                            stringResource(R.string.settings_local_server_status_offline)
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onLocalServerAccent,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        // Power Switch
                        Switch(
                            checked = running,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    playerPreferences.setLocalServerEnabled(enabled)
                                }
                                if (enabled) {
                                    io.github.aedev.flow.localserver.ServerService.start(context)
                                } else {
                                    io.github.aedev.flow.localserver.ServerService.stop(context)
                                }
                            },
                            colors =
                                SwitchDefaults.colors(
                                    checkedThumbColor = onLocalServerAccent,
                                    checkedTrackColor = onLocalServerAccent.copy(alpha = 0.5f),
                                ),
                        )
                    }

                    // Server Info
                    Column {
                        Text(
                            text = stringResource(R.string.settings_item_local_server),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                            color = onLocalServerAccent,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = localServerAddress ?: stringResource(R.string.settings_item_local_server_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = onLocalServerAccent.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Bottom CTA
                    if (running) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.settings_local_server_open_remote),
                                style = MaterialTheme.typography.labelLarge,
                                color = onLocalServerAccent,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = onLocalServerAccent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    }
}

/**
 * Mutes [base] into its "smoky" finish - lower saturation, slightly darker - while keeping its
 * hue, so it always tracks whichever theme color is passed in instead of a fixed color.
 */
private fun smokyVariant(
    base: Color,
    saturationFactor: Float = 0.55f,
    valueFactor: Float = 0.9f,
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    hsv[1] = (hsv[1] * saturationFactor).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * valueFactor).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
