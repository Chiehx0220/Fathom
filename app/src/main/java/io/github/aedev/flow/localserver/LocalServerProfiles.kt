package io.github.aedev.flow.localserver

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.profile.AppProfileSwitchSteps
import io.github.aedev.flow.data.profile.Profile
import io.github.aedev.flow.data.profile.ProfileAvatar
import io.github.aedev.flow.data.profile.ProfileRepository
import io.github.aedev.flow.data.profile.ProfileSwitchGate
import io.github.aedev.flow.data.profile.ProfileSwitcher
import io.github.aedev.flow.data.profile.SwitchResult
import io.github.aedev.flow.data.profile.displayName
import io.github.aedev.flow.ui.theme.ProfileColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalServerProfilesEntryPoint {
    fun profileRepository(): ProfileRepository

    fun profileSwitcher(): ProfileSwitcher

    fun profileSwitchSteps(): AppProfileSwitchSteps

    fun playerPreferences(): PlayerPreferences
}

@Serializable
internal class ProfileDto(
    val id: String,
    val name: String,
    val color: String?,
    val emoji: String?,
)

/** [refused]: lower-case `SwitchRefusal` when `switch` did not start. */
@Serializable
internal class ProfilesDto(
    val running: String,
    val profiles: List<ProfileDto>,
    val switching: Boolean,
    val refused: String?,
    val showInTopBar: Boolean,
)

// Main: player thread.
private val switchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

internal fun RequestContext.handleApiProfiles(params: Query): ProfilesDto {
    val context = dbHelper.appContext
    val entry = EntryPointAccessors.fromApplication(context, LocalServerProfilesEntryPoint::class.java)
    val repository = entry.profileRepository()
    val refused = params["switch"]?.takeIf { it.isNotEmpty() }?.let { startSwitch(context, entry, it) }
    val profiles = runBlocking { repository.state.first() }.profiles.map { it.toDto(context) }
    val showInTopBar = runBlocking { entry.playerPreferences().showProfileInTopBar.first() }
    return ProfilesDto(repository.running().id, profiles, ProfileSwitchGate.isSwitching, refused, showInTopBar)
}

/** Starts the switch; refusal name if rejected. */
private fun startSwitch(
    context: Context,
    entry: LocalServerProfilesEntryPoint,
    target: String,
): String? {
    if (ProfileSwitchGate.isSwitching) return null
    runBlocking { entry.profileSwitchSteps().refusal(target) }?.let { return it.name.lowercase() }
    ServerService.keepAcrossRestart(context)
    switchScope.launch {
        runCatching { entry.profileSwitcher().switchTo(target) }
            .onFailure { serverLog("Profile switch failed: ${it.message}") }
            .onSuccess { if (it !is SwitchResult.Restarting) ServerResume.disarm(context) }
    }
    return null
}

private fun Profile.toDto(context: Context) =
    ProfileDto(
        id = id,
        name = displayName(context),
        color = ProfileColors.seed(colorKey)?.let { "#%06X".format(it.toArgb() and 0xFFFFFF) },
        emoji = avatar.emoji.takeIf { avatar.kind == ProfileAvatar.EMOJI && it.isNotBlank() },
    )
