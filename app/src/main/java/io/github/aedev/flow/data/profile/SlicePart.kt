package io.github.aedev.flow.data.profile

import io.github.aedev.flow.data.local.BackupData
import io.github.aedev.flow.data.local.SettingsBackup

/** What a new profile can copy from the running one. */
enum class SlicePart {
    /** Theme, playback and app preferences. Device settings are shared already. */
    SETTINGS,

    /** Channels and channel groups. */
    SUBSCRIPTIONS,

    /** What the video and music engines learned, with blocked and preferred topics, blocked channels and favourite artists. */
    RECOMMENDATIONS,
}

private const val ONBOARDING_DONE = "onboarding_done"

// This profile's state, not its preferences: what it is in the middle of, and work queued under it.
private val ProfileStateKeys =
    setOf(
        "upcoming_video_reminder_ids",
        "upcoming_video_reminders",
        "deep_flow_active",
        "deep_flow_activated_at",
        "app_icon_suffix",
    )

/**
 * The seed a new profile starts from: only the ticked [parts], and never history, playlists, likes or notes.
 * Settings always come along with an explicit onboarding_done, so a profile without the engine runs onboarding even
 * when the one it was copied from had finished it.
 */
internal fun BackupData.seedFor(parts: Set<SlicePart>): BackupData {
    val settings = settings?.takeIf { SlicePart.SETTINGS in parts }?.withoutProfileState() ?: SettingsBackup()
    return BackupData(
        viewHistory = null,
        searchHistory = null,
        musicSearchHistory = null,
        subscriptions = subscriptions.takeIf { SlicePart.SUBSCRIPTIONS in parts },
        playlists = null,
        playlistVideos = null,
        videos = null,
        subscriptionGroups = subscriptionGroups.takeIf { SlicePart.SUBSCRIPTIONS in parts },
        notes = null,
        likedVideos = null,
        contentPreferences = contentPreferences.takeIf { SlicePart.RECOMMENDATIONS in parts },
        settings = settings.copy(booleans = settings.booleans + (ONBOARDING_DONE to (SlicePart.RECOMMENDATIONS in parts))),
    )
}

private fun SettingsBackup.withoutProfileState() =
    SettingsBackup(
        strings = strings - ProfileStateKeys,
        booleans = booleans - ProfileStateKeys,
        ints = ints - ProfileStateKeys,
        floats = floats - ProfileStateKeys,
        longs = longs - ProfileStateKeys,
        stringSets = stringSets - ProfileStateKeys,
    )
