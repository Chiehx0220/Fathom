package io.github.aedev.flow.ui.screens.onboarding

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.github.aedev.flow.R
import io.github.aedev.flow.data.backup.ImportSource
import io.github.aedev.flow.data.model.Channel

internal const val STAGGER_DELAY_MS = 50L

/** Setup steps in order; Welcome and Ready are outside the progress count. */
enum class OnboardingStep(
    @StringRes val labelRes: Int?,
) {
    WELCOME(null),
    CHANNELS(R.string.onboarding_step_channels),
    ARTISTS(R.string.onboarding_step_artists),
    ALERTS(R.string.onboarding_step_alerts),
    IMPORT(R.string.onboarding_step_import),
    READY(null),
    ;

    val index: Int get() = ordinal

    val showsProgress: Boolean get() = labelRes != null

    companion object {
        val progressSteps: List<OnboardingStep> = entries.filter { it.showsProgress }
    }
}

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.entries.first(),
    val query: String = "",
    val results: List<Channel> = emptyList(),
    val searching: Boolean = false,
    val subscribed: List<Channel> = emptyList(),
    val notifying: Set<String> = emptySet(),
    val importedSources: Set<ImportSource> = emptySet(),
    val completed: Boolean = false,
) {
    fun isSubscribed(channelId: String): Boolean = subscribed.any { it.id == channelId }
}
