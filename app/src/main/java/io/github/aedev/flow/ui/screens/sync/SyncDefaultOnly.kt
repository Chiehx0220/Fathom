package io.github.aedev.flow.ui.screens.sync

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SyncDisabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.topbar.FlowGlobalActionsMode
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.FlowEmptyState

/** Sync outside the profile that syncs: it says where sync runs instead of offering a session. */
@Composable
internal fun SyncDefaultOnly(
    syncProfileName: String,
    onBack: (() -> Unit)?,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.sync_devices_title),
                onBack = onBack,
                globalActions = FlowGlobalActionsMode.None,
            )
        },
    ) { padding ->
        FlowEmptyState(
            title = stringResource(R.string.sync_default_only_title, syncProfileName),
            subtitle = stringResource(R.string.sync_default_only_body),
            icon = Icons.Outlined.SyncDisabled,
            modifier = Modifier.padding(padding),
        )
    }
}
