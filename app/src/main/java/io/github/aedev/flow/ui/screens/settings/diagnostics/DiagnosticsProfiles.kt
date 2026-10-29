package io.github.aedev.flow.ui.screens.settings.diagnostics

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.profile.Profile
import io.github.aedev.flow.data.profile.ProfileRegistry
import io.github.aedev.flow.data.profile.ProfileRegistryState
import io.github.aedev.flow.data.profile.displayName
import io.github.aedev.flow.ui.components.profiles.ProfileAvatar
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.shared.FlowNavRow

/** Debug builds only, until the Profiles screens arrive: add, switch to and delete profiles. Kept out of settings search. */
internal fun SettingsListScope.debugProfiles(
    state: ProfileRegistryState?,
    runningId: String,
    onAdd: () -> Unit,
    onAddCopy: () -> Unit,
    onPhoto: (Uri) -> Unit,
    onSwitch: (String) -> Unit,
    onDelete: (Profile) -> Unit,
) {
    if (state == null) return
    group(key = "diagnostics.profiles", header = R.string.diagnostics_profiles_section, footer = R.string.diagnostics_profiles_footer) {
        state.profiles.forEach { profile ->
            val running = profile.id == runningId
            row("diagnostics.profiles.${profile.id}") { shape ->
                FlowNavRow(
                    title = profile.displayName(LocalContext.current),
                    supportingText =
                        stringResource(
                            if (running) R.string.diagnostics_profile_running else R.string.diagnostics_profile_switch,
                        ),
                    onClick = { onSwitch(profile.id) },
                    enabled = !running,
                    showChevron = false,
                    shape = shape,
                    leadingContent = { ProfileAvatar(profile) },
                    trailingContent =
                        if (running || profile.id == state.defaultId) {
                            null
                        } else {
                            {
                                IconButton(onClick = { onDelete(profile) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.diagnostics_profile_delete))
                                }
                            }
                        },
                )
            }
        }
        if (state.profiles.size < ProfileRegistry.MAX_PROFILES) {
            row("diagnostics.profiles.add") { shape ->
                FlowNavRow(
                    title = stringResource(R.string.diagnostics_profile_add),
                    onClick = onAdd,
                    leadingIcon = Icons.Outlined.PersonAdd,
                    showChevron = false,
                    shape = shape,
                )
            }
            row("diagnostics.profiles.photo") { shape ->
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPhoto) }
                FlowNavRow(
                    title = stringResource(R.string.diagnostics_profile_photo),
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    leadingIcon = Icons.Outlined.AddAPhoto,
                    showChevron = false,
                    shape = shape,
                )
            }
            row("diagnostics.profiles.add_copy") { shape ->
                FlowNavRow(
                    title = stringResource(R.string.diagnostics_profile_add_copy),
                    supportingText = stringResource(R.string.diagnostics_profile_add_copy_summary),
                    onClick = onAddCopy,
                    leadingIcon = Icons.Outlined.ContentCopy,
                    showChevron = false,
                    shape = shape,
                )
            }
        }
    }
}
