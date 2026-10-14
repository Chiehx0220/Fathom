@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.aedev.flow.localserver.remote

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.RemoteState
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class SettingsPage { MAIN, QUALITY, SPEED }

/** Quality needs renditions, speed needs a finite duration, danmaku needs a source that provides it. */
internal fun hasSettings(state: RemoteState): Boolean = state.qualities.isNotEmpty() || state.durationSec > 0.0 || state.danmaku != null

private val Speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** Player settings in a bottom sheet; a selection returns to the main page. */
@Composable
internal fun RemoteSettingsSheet(
    state: RemoteState,
    send: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val toMain = { page = SettingsPage.MAIN }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        // Sheet back handling is disabled: a stacked BackHandler otherwise swallows the first press.
        BackHandler {
            if (page != SettingsPage.MAIN) toMain() else scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            when (page) {
                SettingsPage.MAIN -> {
                    MainPage(state, onOpen = { page = it }, onDanmaku = { send(danmakuCommand(it)) })
                }

                SettingsPage.QUALITY -> {
                    QualityPage(state, onBack = toMain) { id ->
                        send(qualityCommand(id))
                        toMain()
                    }
                }

                SettingsPage.SPEED -> {
                    SpeedPage(state, onBack = toMain) { speed ->
                        send(rateCommand(speed))
                        toMain()
                    }
                }
            }
        }
    }
}

@Composable
private fun MainPage(
    state: RemoteState,
    onOpen: (SettingsPage) -> Unit,
    onDanmaku: (Boolean) -> Unit,
) {
    Text(
        stringResource(R.string.remote_settings),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    if (state.qualities.isNotEmpty()) {
        val chosen = state.qualities.firstOrNull { it.id == state.chosenQuality }
        val current =
            when {
                chosen != null -> chosen.label
                state.quality != null -> stringResource(R.string.remote_quality_auto_now, state.quality)
                else -> stringResource(R.string.remote_quality_auto)
            }
        SettingRow(Icons.Default.Tune, stringResource(R.string.remote_quality), current) { onOpen(SettingsPage.QUALITY) }
    }
    if (state.durationSec > 0.0) {
        SettingRow(Icons.Default.Speed, stringResource(R.string.remote_speed), speedLabel(state.rate)) { onOpen(SettingsPage.SPEED) }
    }
    state.danmaku?.let { on ->
        ListItem(
            headlineContent = { Text(stringResource(R.string.remote_danmaku)) },
            leadingContent = { Icon(Icons.Default.ChatBubble, contentDescription = null) },
            trailingContent = { Switch(checked = on, onCheckedChange = null) },
            modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = onDanmaku),
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SubPageHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.remote_back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = if (selected) ({ Icon(Icons.Default.Check, contentDescription = null) }) else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun QualityPage(
    state: RemoteState,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    SubPageHeader(stringResource(R.string.remote_quality), onBack)
    LazyColumn {
        item {
            ChoiceRow(
                label = stringResource(R.string.remote_quality_auto),
                detail = state.quality?.takeIf { state.chosenQuality == "auto" },
                selected = state.chosenQuality == "auto",
            ) { onPick("auto") }
        }
        items(state.qualities, key = { it.id }) { option ->
            ChoiceRow(
                label = option.label,
                detail = null,
                selected = option.id == state.chosenQuality,
            ) { onPick(option.id) }
        }
    }
}

@Composable
private fun SpeedPage(
    state: RemoteState,
    onBack: () -> Unit,
    onPick: (Float) -> Unit,
) {
    SubPageHeader(stringResource(R.string.remote_speed), onBack)
    LazyColumn {
        items(Speeds, key = { it }) { speed ->
            ChoiceRow(
                label = if (speed == 1f) stringResource(R.string.remote_speed_normal) else speedLabel(speed),
                detail = null,
                selected = abs(state.rate - speed) < 0.01f,
            ) { onPick(speed) }
        }
    }
}

private fun speedLabel(speed: Float): String = if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
