@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.localserver.remote

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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

// Remote keys: ButtonGroup items; row keys share width, pressed key widens, neighbours yield; same height per row (group top-aligns).

/** Page volume step per volume-key press (0..1). */
internal const val VOLUME_STEP = 0.1f

/** Light tick per key (eyes-free use). */
@Composable
internal fun rememberKeyTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/** Key icon; meaning change (play → pause): old icon shrinks, new grows. */
@Composable
internal fun KeyIcon(
    icon: ImageVector,
    size: Dp,
    description: String? = null,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = icon,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec<Float>()) + scaleIn(motion.fastSpatialSpec<Float>(), initialScale = 0.4f)) togetherWith
                (fadeOut(motion.defaultEffectsSpec<Float>()) + scaleOut(motion.fastSpatialSpec<Float>(), targetScale = 0.4f))
        },
        label = "keyIcon",
    ) { shown ->
        Icon(shown, contentDescription = description, modifier = Modifier.size(size))
    }
}

internal val KeyHeight = 52.dp
internal val ShortcutHeight = 60.dp
internal val TransportHeight = 80.dp

/** Wide key, icon beside label. */
internal fun ButtonGroupScope.wideKey(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) = customItem({
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberKeyTick()
    FilledTonalButton(
        onClick = {
            tick()
            onClick()
        },
        shapes = ButtonDefaults.shapes(),
        interactionSource = interaction,
        modifier = Modifier.weight(1f).height(KeyHeight).animateWidth(interaction),
    ) {
        KeyIcon(icon, 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(label))
    }
}) {}

/** Key, icon over short label (four per row). */
internal fun ButtonGroupScope.shortcutKey(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
) = customItem({
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberKeyTick()
    FilledTonalButton(
        onClick = {
            tick()
            onClick()
        },
        shapes = ButtonDefaults.shapes(),
        interactionSource = interaction,
        modifier = Modifier.weight(1f).height(ShortcutHeight).animateWidth(interaction),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KeyIcon(icon, 22.dp)
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}) {}

/** Icon-only tool: plain key or switch lit while [toggled]. */
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
        FilledTonalIconButton(onClick = {
            tick()
            onClick()
        }, shapes = IconButtonDefaults.shapes(), interactionSource = interaction, modifier = modifier) {
            KeyIcon(icon, 24.dp, stringResource(description))
        }
    } else {
        FilledIconToggleButton(
            checked = toggled,
            onCheckedChange = {
                tick()
                onClick()
            },
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
            KeyIcon(icon, 24.dp, stringResource(description))
        }
    }
}) {}

/** Playback key: [weight] = row share, [strong] = filled instead of tonal. */
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
        KeyIcon(icon, iconSize, stringResource(description))
    }
    if (strong) {
        FilledIconButton(onClick = {
            tick()
            onClick()
        }, shapes = IconButtonDefaults.shapes(), interactionSource = interaction, modifier = modifier, content = content)
    } else {
        FilledTonalIconButton(onClick = {
            tick()
            onClick()
        }, shapes = IconButtonDefaults.shapes(), interactionSource = interaction, modifier = modifier, content = content)
    }
}) {}
