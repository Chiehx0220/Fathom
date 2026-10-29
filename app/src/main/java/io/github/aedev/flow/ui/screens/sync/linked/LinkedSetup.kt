package io.github.aedev.flow.ui.screens.sync.linked

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.sync.SyncActionRow
import io.github.aedev.flow.ui.screens.sync.SyncManualEntryContent
import io.github.aedev.flow.ui.screens.sync.SyncOption
import io.github.aedev.flow.ui.screens.sync.SyncOptions
import io.github.aedev.flow.ui.screens.sync.SyncScanContent
import io.github.aedev.flow.ui.screens.sync.SyncStepHeader

private enum class Setup { CHOOSE, CREATE, JOIN_SCAN, JOIN_PASTE, JOIN_ADMIN, RESTORE }

/**
 * Linking a device that isn't in a group yet: create one, join one with a member's code, or restore
 * one with the recovery words. [hostOnly] devices have no camera, so they join by pasting or by showing a code.
 */
@Composable
internal fun LinkedSetup(
    hostOnly: Boolean,
    busy: Boolean,
    onCreate: (url: String, adminToken: String) -> Unit,
    onCreateOnNetwork: () -> Unit,
    onEnterCode: (code: String) -> Unit,
    onJoinWithAdmin: (url: String, adminToken: String) -> Unit,
    onShowCode: () -> Unit,
    onRestore: (url: String, words: String) -> Unit,
) {
    var setup by rememberSaveable { mutableStateOf(Setup.CHOOSE) }
    val back = { setup = Setup.CHOOSE }
    when (setup) {
        Setup.CHOOSE -> {
            SyncStepHeader(
                icon = Icons.Outlined.Devices,
                title = stringResource(R.string.linked_title),
                body = stringResource(R.string.linked_intro),
            )
            SyncOptions(
                listOf(
                    SyncOption(
                        Icons.Outlined.AddLink,
                        stringResource(R.string.linked_create_title),
                        stringResource(R.string.linked_create_body),
                    ) {
                        setup = Setup.CREATE
                    },
                    SyncOption(
                        Icons.Outlined.Wifi,
                        stringResource(R.string.linked_create_lan_title),
                        stringResource(R.string.linked_create_lan_body),
                    ) {
                        if (!busy) onCreateOnNetwork()
                    },
                    SyncOption(
                        Icons.Outlined.QrCodeScanner,
                        stringResource(R.string.linked_join_title),
                        stringResource(R.string.linked_join_body),
                    ) {
                        setup = if (hostOnly) Setup.JOIN_PASTE else Setup.JOIN_SCAN
                    },
                    SyncOption(
                        Icons.Outlined.Dns,
                        stringResource(R.string.linked_join_admin_title),
                        stringResource(R.string.linked_join_admin_body),
                    ) {
                        setup = Setup.JOIN_ADMIN
                    },
                    SyncOption(
                        Icons.Outlined.QrCode2,
                        stringResource(R.string.linked_show_code_title),
                        stringResource(R.string.linked_show_code_body),
                    ) {
                        onShowCode()
                    },
                    SyncOption(
                        Icons.Outlined.Key,
                        stringResource(R.string.linked_restore_title),
                        stringResource(R.string.linked_restore_body),
                    ) {
                        setup = Setup.RESTORE
                    },
                ),
            )
        }

        Setup.CREATE -> {
            var url by rememberSaveable { mutableStateOf("") }
            var token by rememberSaveable { mutableStateOf("") }
            SyncStepHeader(icon = Icons.Outlined.AddLink, title = stringResource(R.string.linked_create_title))
            ServerField(url) { url = it }
            AdminTokenField(token) { token = it }
            SyncActionRow(
                confirmLabel = stringResource(R.string.linked_create),
                onConfirm = { onCreate(url, token) },
                confirmEnabled = !busy && url.isNotBlank() && token.isNotBlank(),
                dismissLabel = stringResource(R.string.linked_back),
                onDismiss = back,
            )
        }

        Setup.JOIN_SCAN -> {
            SyncScanContent(prompt = stringResource(R.string.linked_scan_member_code), onScanned = onEnterCode)
            SyncActionRow(
                confirmLabel = stringResource(R.string.sync_paste_connection_data),
                onConfirm = { setup = Setup.JOIN_PASTE },
                dismissLabel = stringResource(R.string.linked_back),
                onDismiss = back,
            )
        }

        Setup.JOIN_PASTE -> {
            SyncManualEntryContent(onSubmit = onEnterCode)
            SyncActionRow(confirmLabel = stringResource(R.string.linked_back), onConfirm = back)
        }

        Setup.JOIN_ADMIN -> {
            var url by rememberSaveable { mutableStateOf("") }
            var token by rememberSaveable { mutableStateOf("") }
            SyncStepHeader(icon = Icons.Outlined.Dns, title = stringResource(R.string.linked_join_admin_title))
            ServerField(url) { url = it }
            AdminTokenField(token) { token = it }
            SyncActionRow(
                confirmLabel = stringResource(R.string.linked_join_admin_ask),
                onConfirm = { onJoinWithAdmin(url, token) },
                confirmEnabled = !busy && url.isNotBlank() && token.isNotBlank(),
                dismissLabel = stringResource(R.string.linked_back),
                onDismiss = back,
            )
        }

        Setup.RESTORE -> {
            var url by rememberSaveable { mutableStateOf("") }
            var words by rememberSaveable { mutableStateOf("") }
            SyncStepHeader(icon = Icons.Outlined.Key, title = stringResource(R.string.linked_restore_title))
            ServerField(url) { url = it }
            OutlinedTextField(
                value = words,
                onValueChange = { words = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.linked_recovery_words_field)) },
                minLines = 3,
            )
            SyncActionRow(
                confirmLabel = stringResource(R.string.linked_restore),
                onConfirm = { onRestore(url, words) },
                confirmEnabled = !busy && url.isNotBlank() && words.isNotBlank(),
                dismissLabel = stringResource(R.string.linked_back),
                onDismiss = back,
            )
        }
    }
}

@Composable
private fun ServerField(
    url: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = url,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.linked_server_url)) },
        placeholder = { Text(stringResource(R.string.linked_server_url_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true,
    )
}

@Composable
private fun AdminTokenField(
    token: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = token,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.linked_admin_token)) },
        supportingText = { Text(stringResource(R.string.linked_admin_token_hint)) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
    )
}
