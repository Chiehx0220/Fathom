package io.github.aedev.flow.ui.tv.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.profile.Profile
import io.github.aedev.flow.data.profile.displayName
import io.github.aedev.flow.ui.components.profiles.ProfileAvatar
import io.github.aedev.flow.ui.components.profiles.ProfileSwitchViewModel
import io.github.aedev.flow.ui.components.settings.SettingsDestination
import io.github.aedev.flow.ui.components.settings.SettingsTarget

private val CardAvatarSize = 96.dp
private val CardSpacing = 28.dp
private val SectionSpacing = 40.dp
private val CardPadding = 18.dp

/** Whether a launch first asks who is watching. Only a cold start does, and a link opens where it points. */
internal fun asksWhoIsWatching(
    profileCount: Int,
    askEnabled: Boolean,
    afterSwitch: Boolean,
    hasLink: Boolean,
): Boolean = profileCount >= 2 && askEnabled && !afterSwitch && !hasLink

/** Answered once per process, so the activity coming back or being recreated never asks again. */
private object WhoIsWatchingAnswer {
    @Volatile
    var answered = false
}

/**
 * Stands in front of the TV app on a cold start with two or more profiles, so Home never loads for the wrong
 * profile. [content] gets the settings page to open first when the picker sent there.
 */
@Composable
fun TvProfileGate(
    hasLink: Boolean,
    content: @Composable (startSettings: SettingsTarget?) -> Unit,
) {
    if (WhoIsWatchingAnswer.answered) {
        content(null)
        return
    }
    val viewModel: ProfileSwitchViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var startSettings by remember { mutableStateOf<SettingsTarget?>(null) }
    var picked by remember { mutableStateOf(false) }
    val registry = state ?: return
    val ask = asksWhoIsWatching(registry.profiles.size, registry.askWhosWatching, viewModel.afterSwitch, hasLink)
    if (!ask || picked) {
        SideEffect { WhoIsWatchingAnswer.answered = true }
        content(startSettings)
        return
    }
    TvWhoIsWatchingScreen(
        profiles = registry.profiles,
        runningId = viewModel.runningId,
        onPick = { profile ->
            if (profile.id == viewModel.runningId) picked = true else viewModel.switchTo(profile.id)
        },
        onManage = {
            startSettings = SettingsTarget(SettingsDestination.PROFILES)
            picked = true
        },
    )
}

/** Every profile as a card, focus starting on the one in use; Back leaves the app. */
@Composable
private fun TvWhoIsWatchingScreen(
    profiles: List<Profile>,
    runningId: String,
    onPick: (Profile) -> Unit,
    onManage: () -> Unit,
) {
    val context = LocalContext.current
    val runningFocus = remember { FocusRequester() }
    BackHandler { (context as? Activity)?.finish() }
    LaunchedEffect(Unit) { runningFocus.requestFocus() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.profiles_whos_watching), style = MaterialTheme.typography.displaySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(CardSpacing)) {
                profiles.forEach { profile ->
                    Surface(
                        onClick = { onPick(profile) },
                        modifier = if (profile.id == runningId) Modifier.focusRequester(runningFocus) else Modifier,
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(
                            modifier = Modifier.padding(CardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ProfileAvatar(profile, size = CardAvatarSize)
                            Text(profile.displayName(context), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            OutlinedButton(onClick = onManage) { Text(stringResource(R.string.profiles_manage)) }
        }
    }
}
