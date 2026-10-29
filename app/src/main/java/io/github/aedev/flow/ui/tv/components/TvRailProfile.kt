package io.github.aedev.flow.ui.tv.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.profile.displayName
import io.github.aedev.flow.ui.components.profiles.ProfileAvatar
import io.github.aedev.flow.ui.components.profiles.ProfileSwitchSheet
import io.github.aedev.flow.ui.components.profiles.ProfileSwitchViewModel
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsTarget

private val AvatarSize = 32.dp
private val NameMaxWidth = 160.dp

/**
 * The rail's header: who is watching, in the logo's place and at its height, so the rail's items stay where they
 * were. OK opens the profile sheet, which the TV presents as a side panel. The rail's own entry goes to the selected
 * tab, so this is reached only by moving up to it.
 */
@Composable
fun TvRailProfile(
    expanded: Boolean,
    onOpenSettings: (SettingsTarget) -> Unit,
    viewModel: ProfileSwitchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val running = state?.profiles?.firstOrNull { it.id == viewModel.runningId } ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    val name = running.displayName(context)
    val description = stringResource(R.string.profiles_action_description, name)
    val motion = MaterialTheme.motionScheme

    Surface(
        onClick = { open = true },
        modifier = Modifier.padding(start = 20.dp).semantics { contentDescription = description },
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        Row(modifier = Modifier.padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(running, size = AvatarSize)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandHorizontally(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.fastSpatialSpec()),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = NameMaxWidth).padding(start = 12.dp, end = 8.dp),
                )
            }
        }
    }

    if (open) {
        ProfileSwitchSheet(
            profiles = state?.profiles.orEmpty(),
            runningId = viewModel.runningId,
            onSwitch = viewModel::switchTo,
            onAdd = { onOpenSettings(SettingsTarget(SettingsDestination.PROFILE_EDIT)) },
            onManage = { onOpenSettings(SettingsTarget(SettingsDestination.PROFILES)) },
            onDismiss = { open = false },
        )
    }
}
