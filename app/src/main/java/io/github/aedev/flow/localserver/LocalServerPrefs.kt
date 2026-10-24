package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.DEEP_FLOW_NEVER_EXPIRES_HOURS
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SearchHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private fun HistoryDbHelper.searchHistoryRepository() = SearchHistoryRepository(appContext)

/** `SearchHistoryRepository` (DataStore). Plain query-string list; [deleteSearchQuery] resolves id by text. */
fun HistoryDbHelper.addSearchQuery(query: String?) {
    if (query.isNullOrBlank()) return
    runBlocking { searchHistoryRepository().saveSearchQuery(query.trim()) }
}

fun HistoryDbHelper.searchHistory(): List<String> =
    runBlocking {
        searchHistoryRepository().getRecentSearches(10).map { it.query }
    }

fun HistoryDbHelper.deleteSearchQuery(query: String?) {
    if (query.isNullOrBlank()) return
    runBlocking {
        val repo = searchHistoryRepository()
        val item = repo.getSearchHistoryFlow().first().find { it.query == query }
        if (item != null) repo.deleteSearchItem(item.id)
    }
}

internal fun HistoryDbHelper.playerPreferences() = PlayerPreferences(appContext)

/** `PlayerPreferences` (DataStore); `hideWatched` → home-feed toggle, `videoQuality` → Wi-Fi slot. */
fun HistoryDbHelper.hideWatched(): Boolean = runBlocking { playerPreferences().hideWatchedVideosFromHome.first() }

fun HistoryDbHelper.setHideWatched(enabled: Boolean) {
    runBlocking { playerPreferences().setHideWatchedVideosFromHome(enabled) }
}

fun HistoryDbHelper.hideShorts(): Boolean = runBlocking { !playerPreferences().shortsContentEnabled.first() }

fun HistoryDbHelper.setHideShorts(hide: Boolean) {
    runBlocking { playerPreferences().setShortsContentEnabled(!hide) }
}

fun HistoryDbHelper.videoQuality(): String = runBlocking { playerPreferences().defaultQualityWifi.first().label }

/** [endsAt]: epoch ms of timer expiry; 0 = no timer or inactive. */
data class DeepFlowStatus(
    val active: Boolean,
    val endsAt: Long,
)

fun HistoryDbHelper.deepFlow(): DeepFlowStatus =
    runBlocking {
        val preferences = playerPreferences()
        if (!preferences.isDeepFlowCurrentlyActive()) return@runBlocking DeepFlowStatus(active = false, endsAt = 0L)
        DeepFlowStatus(
            active = true,
            endsAt = deepFlowEndsAt(preferences.deepFlowActivatedAt.first(), preferences.deepFlowExpireHours.first()),
        )
    }

fun HistoryDbHelper.setDeepFlow(enabled: Boolean) {
    runBlocking { playerPreferences().setDeepFlowActive(enabled) }
}

internal fun deepFlowEndsAt(
    activatedAt: Long,
    expireHours: Int,
): Long = if (expireHours == DEEP_FLOW_NEVER_EXPIRES_HOURS) 0L else activatedAt + expireHours * 3_600_000L
