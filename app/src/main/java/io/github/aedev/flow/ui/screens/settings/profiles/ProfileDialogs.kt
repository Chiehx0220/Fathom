package io.github.aedev.flow.ui.screens.settings.profiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.profile.ProfileRegistry
import io.github.aedev.flow.ui.components.profiles.switchRefusalText
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog

@Composable
internal fun RenameProfileDialog(
    initialName: String,
    otherNames: List<String>,
    allowBlank: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val issue = nameIssue(name, otherNames).takeUnless { it == NameIssue.BLANK && allowBlank }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profiles_rename)) },
        text = { ProfileNameField(name = name, onNameChange = { name = it }, taken = issue == NameIssue.TAKEN) },
        confirmButton = {
            TextButton(enabled = issue == null, onClick = {
                onConfirm(name.trim())
                onDismiss()
            }) { Text(stringResource(R.string.profiles_rename)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun ProfileNameField(
    name: String,
    onNameChange: (String) -> Unit,
    taken: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = name,
        onValueChange = { onNameChange(it.take(PROFILE_NAME_MAX_LENGTH)) },
        label = { Text(stringResource(R.string.profiles_name)) },
        isError = taken,
        supportingText = if (taken) ({ Text(stringResource(R.string.profiles_name_taken)) }) else null,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun DeleteProfileDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.profiles_delete_title, name)) },
        text = { Text(stringResource(R.string.profiles_delete_body)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(R.string.profiles_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Says once what a create, delete, photo or switch did. */
@Composable
internal fun ProfilesMessageEffect(
    message: ProfilesMessage?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
) {
    val text =
        when (message) {
            ProfilesMessage.Deleted -> stringResource(R.string.profiles_deleted)
            ProfilesMessage.LimitReached -> stringResource(R.string.profiles_limit_reached, ProfileRegistry.MAX_PROFILES)
            ProfilesMessage.CopyFailed -> stringResource(R.string.profiles_copy_failed)
            ProfilesMessage.PhotoFailed -> stringResource(R.string.profiles_photo_failed)
            is ProfilesMessage.SwitchRefused -> stringResource(switchRefusalText(message.reason))
            null -> null
        }
    LaunchedEffect(message) {
        if (text == null) return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(text)
        } finally {
            onConsumed()
        }
    }
}
