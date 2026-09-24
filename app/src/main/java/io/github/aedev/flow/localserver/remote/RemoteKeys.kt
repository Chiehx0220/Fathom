@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The keys every screen of the remote is built from. Each one is an item of a ButtonGroup, so the keys in a row
 * share its width and a pressed key widens while its neighbours give way. Every key of one row has the same
 * height on purpose: a group lines its items up at the top, so keys of different heights would not share a line.
 */

/** How far one volume key press moves the page's volume (0..1). */
internal const val VOLUME_STEP = 0.1f

/** A light tick under the finger for every key: a remote is used without looking. */
@Composable
internal fun rememberKeyTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

internal val KeyHeight = 52.dp
internal val ShortcutHeight = 60.dp
internal val TransportHeight = 80.dp

/** A wide key with the icon beside its label. */
internal fun ButtonGroupScope.wideKey(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) = customItem({
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberKeyTick()
    FilledTonalButton(
        onClick = { tick(); onClick() },
        shapes = ButtonDefaults.shapes(),
        interactionSource = interaction,
        modifier = Modifier.weight(1f).height(KeyHeight).animateWidth(interaction),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
}) {}

/** A key with the icon over a short label, so four of them fit in a row. */
internal fun ButtonGroupScope.shortcutKey(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) = customItem({
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberKeyTick()
    FilledTonalButton(
        onClick = { tick(); onClick() },
        shapes = ButtonDefaults.shapes(),
        interactionSource = interaction,
        modifier = Modifier.weight(1f).height(ShortcutHeight).animateWidth(interaction),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}) {}

/** An icon-only tool: a plain key, or a switch that lights up while [toggled] is true. */
internal fun ButtonGroupScope.toolKey(
    icon: ImageVector,
    description: Int,
    toggled: Boolean? = null,
    onClick: () -> Unit,
) = customItem({
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberKeyTick()
    val modifier = Modifier.weight(1f).height(KeyHeight).animateWidth(interaction)
    if (toggled == null) {
        FilledTonalIconButton(onClick = { tick(); onClick() }, shapes = IconButtonDefaults.shapes(), interactionSource = interaction, modifier = modifier) {
            Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(24.dp))
        }
    } else {
        FilledIconToggleButton(
            checked = toggled,
            onCheckedChange = { tick(); onClick() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors =
                IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            interactionSource = interaction,
            modifier = modifier,
        ) {
            Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(24.dp))
        }
    }
}) {}

/** A playback key: [weight] is its share of the row, [strong] the filled colour instead of the tonal one. */
internal fun ButtonGroupScope.transportKey(
    icon: ImageVector,
    description: Int,
    weight: Float,
    iconSize: Dp,
    strong: Boolean = false,
    onClick: () -> Unit,
) = customItem({
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberKeyTick()
    val modifier = Modifier.weight(weight).height(TransportHeight).animateWidth(interaction)
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(iconSize))
    }
    if (strong) {
        FilledIconButton(onClick = { tick(); onClick() }, shapes = IconButtonDefaults.shapes(), interactionSource = interaction, modifier = modifier, content = content)
    } else {
        FilledTonalIconButton(onClick = { tick(); onClick() }, shapes = IconButtonDefaults.shapes(), interactionSource = interaction, modifier = modifier, content = content)
    }
}) {}
