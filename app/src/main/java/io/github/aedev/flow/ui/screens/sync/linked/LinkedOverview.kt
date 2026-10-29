package io.github.aedev.flow.ui.screens.sync.linked

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.sync2.data.LAN_STORE
import io.github.aedev.flow.sync2.service.LinkedDevice
import io.github.aedev.flow.sync2.service.LinkedError
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.screens.sync.SyncActionRow
import io.github.aedev.flow.ui.screens.sync.SyncCard
import io.github.aedev.flow.ui.screens.sync.SyncInfoRow
import io.github.aedev.flow.ui.screens.sync.SyncRowGroup
import io.github.aedev.flow.ui.screens.sync.SyncStepHeader

private const val NAME_LIMIT = 64

/** A linked device's group: where it syncs, how the last sync went, and its devices. */
@Composable
internal fun LinkedOverview(
    state: LinkedUiState,
    onSyncNow: () -> Unit,
    onAddDevice: () -> Unit,
    /** Null on a device without a camera. */
    onScanNewDevice: (() -> Unit)?,
    onRename: (id: String, name: String) -> Unit,
    onRemove: (id: String) -> Unit,
    onForget: () -> Unit,
    onAccept: (id: String) -> Unit,
    onDecline: (id: String) -> Unit,
) {
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    if (state.error == LinkedError.REMOVED) {
        SyncStepHeader(
            icon = Icons.Outlined.ErrorOutline,
            title = stringResource(R.string.linked_removed_title),
            body = stringResource(R.string.linked_removed_body),
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        SyncActionRow(confirmLabel = stringResource(R.string.linked_forget), onConfirm = onForget, confirmEnabled = !state.busy)
        return
    }

    val lastSync =
        state.lastSync?.let { stringResource(R.string.linked_last_sync, DateUtils.getRelativeTimeSpanString(it.atMs)) }
            ?: stringResource(R.string.linked_last_sync_never)
    SyncRowGroup(count = 2) { index, shape ->
        if (index == 0) {
            FlowNavRow(
                title = stringResource(R.string.linked_server),
                onClick = {},
                supportingText = if (state.storeUrl == LAN_STORE) stringResource(R.string.linked_server_lan) else state.storeUrl,
                leadingIcon = Icons.Outlined.Cloud,
                showChevron = false,
                shape = shape,
            )
        } else {
            FlowNavRow(
                title = stringResource(if (state.syncing) R.string.linked_syncing else R.string.linked_sync_now),
                onClick = onSyncNow,
                supportingText = lastSync,
                enabled = !state.busy && !state.syncing,
                shape = shape,
            )
        }
    }
    state.error?.let { error ->
        SyncCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
            Text(error.message(), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
    if (!state.recoverySaved) SyncInfoRow(icon = Icons.Outlined.Warning, text = stringResource(R.string.linked_recovery_unsaved))

    state.requests.forEach { request ->
        SyncCard {
            Text(
                stringResource(R.string.linked_join_request, request.name),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        SyncActionRow(
            confirmLabel = stringResource(R.string.linked_accept),
            onConfirm = { onAccept(request.id) },
            confirmEnabled = !state.busy,
            dismissLabel = stringResource(R.string.linked_decline),
            onDismiss = { onDecline(request.id) },
        )
    }
    DeviceList(state.devices.orEmpty(), state.nearby, state.busy, onRename, onRemove)
    onScanNewDevice?.let {
        SyncActionRow(confirmLabel = stringResource(R.string.linked_scan_new_device), onConfirm = it, confirmEnabled = !state.busy)
    }
    SyncActionRow(
        confirmLabel = stringResource(R.string.linked_add_device),
        onConfirm = onAddDevice,
        confirmEnabled = !state.busy,
        dismissLabel = stringResource(R.string.linked_forget),
        onDismiss = { confirmForget = true },
    )
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text(stringResource(R.string.linked_forget_title)) },
            text = { Text(stringResource(R.string.linked_forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    onForget()
                }) { Text(stringResource(R.string.linked_forget)) }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text(stringResource(R.string.linked_cancel)) } },
        )
    }
}

@Composable
private fun DeviceList(
    devices: List<LinkedDevice>,
    nearby: Map<String, Long>,
    busy: Boolean,
    onRename: (id: String, name: String) -> Unit,
    onRemove: (id: String) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Text(stringResource(R.string.linked_devices), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium)
    SyncRowGroup(count = devices.size) { index, shape ->
        val device = devices[index]
        FlowNavRow(
            title = device.name,
            onClick = { open = device.id },
            supportingText =
                when {
                    device.thisDevice -> {
                        stringResource(R.string.linked_this_device)
                    }

                    device.id in nearby -> {
                        stringResource(
                            R.string.linked_device_nearby,
                            device.platform,
                            synced(nearby.getValue(device.id)),
                        )
                    }

                    device.seen != null -> {
                        stringResource(R.string.linked_device_synced, device.platform, synced(device.seen))
                    }

                    else -> {
                        device.platform
                    }
                },
            leadingIcon = if (device.platform == "android") Icons.Outlined.PhoneAndroid else Icons.Outlined.Computer,
            enabled = !busy,
            shape = shape,
        )
    }
    devices.firstOrNull { it.id == open }?.let { device ->
        DeviceDialog(
            device = device,
            onRename = { name ->
                open = null
                onRename(device.id, name)
            },
            onRemove = {
                open = null
                onRemove(device.id)
            },
            onDismiss = { open = null },
        )
    }
}

private fun synced(atMs: Long): String = DateUtils.getRelativeTimeSpanString(atMs).toString()

/** Renames a device, or, for another device, removes it after saying what that means. */
@Composable
private fun DeviceDialog(
    device: LinkedDevice,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(device.name) }
    var removing by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (removing) stringResource(R.string.linked_remove_title, device.name) else stringResource(R.string.linked_rename_title),
            )
        },
        text = {
            if (removing) {
                Text(stringResource(R.string.linked_remove_body))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(NAME_LIMIT) },
                        label = { Text(stringResource(R.string.linked_device_name)) },
                        singleLine = true,
                    )
                    if (!device.thisDevice) {
                        TextButton(onClick = { removing = true }) { Text(stringResource(R.string.linked_remove)) }
                    }
                }
            }
        },
        confirmButton = {
            if (removing) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.linked_remove)) }
            } else {
                TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.linked_save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.linked_cancel)) } },
    )
}
