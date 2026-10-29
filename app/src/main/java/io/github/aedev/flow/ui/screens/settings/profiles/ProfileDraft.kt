package io.github.aedev.flow.ui.screens.settings.profiles

import android.net.Uri
import io.github.aedev.flow.data.profile.ProfileAvatar
import io.github.aedev.flow.data.profile.SlicePart

internal const val PROFILE_NAME_MAX_LENGTH = 24

/** What the editor holds before it is saved. [photo] is a newly picked image, not yet stored. */
internal data class ProfileDraft(
    val name: String,
    val colorKey: String,
    val avatar: ProfileAvatar,
    val photo: Uri? = null,
    val copy: Set<SlicePart> = emptySet(),
)

internal enum class NameIssue { BLANK, TAKEN }

/** Why [name] can't be saved, given the names the other profiles show; null when it can. */
internal fun nameIssue(
    name: String,
    otherNames: List<String>,
): NameIssue? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> NameIssue.BLANK
        otherNames.any { it.trim().equals(trimmed, ignoreCase = true) } -> NameIssue.TAKEN
        else -> null
    }
}
