package io.github.aedev.flow.localserver

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.recommendation.BlockedChannelNames
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalServerSettingsEntryPoint {
    fun blockedChannelNames(): BlockedChannelNames
}

// ---- Blocked channels ----

@Serializable
internal class BlockedChannelDto(
    val id: String,
    val name: String,
)

@Serializable
internal class BlockedDto(
    val channels: List<BlockedChannelDto>,
)

/** Blocked channels; `id` = channel URL, name if known. */
internal fun RequestContext.handleApiBlocked(): BlockedDto {
    val ids = dbHelper.blockedChannelIds()
    val entry = EntryPointAccessors.fromApplication(dbHelper.appContext, LocalServerSettingsEntryPoint::class.java)
    val names = runBlocking { entry.blockedChannelNames().known(ids) }
    return BlockedDto(
        ids
            .map { id -> BlockedChannelDto(blockedChannelUrl(id), names[id].orEmpty()) }
            .sortedBy { it.name.lowercase().ifEmpty { "￿" } },
    )
}

private fun blockedChannelUrl(id: String) = if (id.all(Char::isDigit)) LocalServerBilibili.channelUrl(id) else channelIdToUrl(id)

// ---- SponsorBlock ----

/** Web-supported actions; phone-only ones map to nearest. */
private val WEB_ACTIONS =
    listOf(SponsorBlockAction.SKIP, SponsorBlockAction.SKIP_BUTTON, SponsorBlockAction.IGNORE, SponsorBlockAction.DISABLED)

/** Web action for a segment; null = drop. */
internal fun SponsorBlockAction.webAction(): String? =
    when (this) {
        SponsorBlockAction.SKIP -> "skip"
        SponsorBlockAction.SKIP_ONCE -> "once"
        SponsorBlockAction.SKIP_BUTTON, SponsorBlockAction.SHOW_TOAST -> "button"
        SponsorBlockAction.MUTE, SponsorBlockAction.IGNORE -> "show"
        SponsorBlockAction.DISABLED -> null
    }

/** SponsorBlock bridge categories. */
internal val WEB_CATEGORIES = listOf("sponsor", "intro", "outro", "selfpromo", "interaction", "music_offtopic")

@Serializable
internal class SponsorBlockCategoryDto(
    val id: String,
    val action: String,
)

@Serializable
internal class SponsorBlockDto(
    val enabled: Boolean,
    val categories: List<SponsorBlockCategoryDto>,
    val actions: List<String>,
)

/** `enabled`, `category` + `action` saved first; off = skip button. */
internal fun RequestContext.handleApiSponsorBlock(params: Query): SponsorBlockDto {
    val preferences = dbHelper.playerPreferences()
    params.bool("enabled")?.let { runBlocking { preferences.setSponsorBlockEnabled(it) } }
    val category = params["category"]
    val action = params["action"]?.let { name -> WEB_ACTIONS.firstOrNull { it.name == name } }
    if (category in WEB_CATEGORIES && action != null) runBlocking { preferences.setSbActionForCategory(category!!, action) }
    return SponsorBlockDto(
        enabled = runBlocking { preferences.sponsorBlockEnabled.first() },
        categories = WEB_CATEGORIES.map { SponsorBlockCategoryDto(it, runBlocking { preferences.sbActionForCategory(it).first() }.name) },
        actions = WEB_ACTIONS.map { it.name },
    )
}

/** Per-category action for `/api/v1/sponsor`; `button` while off. */
internal fun RequestContext.sponsorActions(): Map<String, SponsorBlockAction?> {
    val preferences = dbHelper.playerPreferences()
    val enabled = runBlocking { preferences.sponsorBlockEnabled.first() }
    return WEB_CATEGORIES.associateWith { category ->
        if (enabled) runBlocking { preferences.sbActionForCategory(category).first() } else SponsorBlockAction.SKIP_BUTTON
    }
}

// ---- Wellbeing ----

@Serializable
internal class WellbeingDto(
    val breakEnabled: Boolean,
    val breakMinutes: Int,
    val breakOptions: List<Int>,
)

private val BREAK_OPTIONS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

/** Break reminder; `breakEnabled`, `breakMinutes` saved first; bedtime/daily limit phone-only. */
internal fun RequestContext.handleApiWellbeing(params: Query): WellbeingDto {
    val data = LocalDataManager(dbHelper.appContext)
    params.bool("breakEnabled")?.let { runBlocking { data.setBreakReminder(it) } }
    params["breakMinutes"]?.toIntOrNull()?.takeIf { it in BREAK_OPTIONS }?.let { runBlocking { data.setBreakFrequency(it) } }
    return WellbeingDto(runBlocking { data.breakReminder.first() }, runBlocking { data.breakFrequency.first() }, BREAK_OPTIONS)
}
