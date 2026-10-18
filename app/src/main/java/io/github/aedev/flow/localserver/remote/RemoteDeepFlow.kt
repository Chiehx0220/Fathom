package io.github.aedev.flow.localserver.remote

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.components.shared.DeepFlowState
import io.github.aedev.flow.ui.components.shared.deepFlowStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private fun PlayerPreferences.deepFlowState(): Flow<DeepFlowState> =
    combine(deepFlowActive, deepFlowActivatedAt, deepFlowExpireHours) { active, activatedAt, hours ->
        DeepFlowState(active = active, activatedAt = activatedAt, expireHours = hours)
    }

/** Deep Flow toggle chip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeepFlowToggle() {
    val context = LocalContext.current
    val preferences = remember { PlayerPreferences(context) }
    val scope = rememberCoroutineScope()
    val state by remember(preferences) { preferences.deepFlowState() }.collectAsStateWithLifecycle(initialValue = DeepFlowState())
    val status = deepFlowStatus(state)
    val on = status != null
    FilterChip(
        selected = on,
        onClick = { scope.launch { preferences.setDeepFlowActive(!on) } },
        label = { Text(stringResource(R.string.remote_deep_flow)) },
        leadingIcon = {
            Icon(
                if (on) Icons.Default.Check else Icons.Outlined.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        modifier = Modifier.semantics { status?.let { stateDescription = it } },
    )
}
