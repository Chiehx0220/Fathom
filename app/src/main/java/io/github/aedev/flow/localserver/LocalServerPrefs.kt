package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SearchHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private fun HistoryDbHelper.searchHistoryRepository() = SearchHistoryRepository(appContext)

/** `SearchHistoryRepository` (DataStore), same store as native's search bar. HTTP contract is a
 * plain query-string list, so [nativeDeleteSearchQuery] resolves id by query text. */
fun HistoryDbHelper.nativeAddSearchQuery(query: String?) {
    if (query.isNullOrBlank()) return
    runBlocking { searchHistoryRepository().saveSearchQuery(query.trim()) }
}

fun HistoryDbHelper.nativeSearchHistory(): List<String> =
    runBlocking {
        searchHistoryRepository().getRecentSearches(10).map { it.query }
    }

fun HistoryDbHelper.nativeDeleteSearchQuery(query: String?) {
    if (query.isNullOrBlank()) return
    runBlocking {
        val repo = searchHistoryRepository()
        val item = repo.getSearchHistoryFlow().first().find { it.query == query }
        if (item != null) repo.deleteSearchItem(item.id)
    }
}

internal fun HistoryDbHelper.playerPreferences() = PlayerPreferences(appContext)

/** `PlayerPreferences` (DataStore). `hideWatched` maps to native's home-feed toggle (Local Server
 * has one combined feed, not separate home/subscriptions screens). `videoQuality` maps to the
 * Wi-Fi quality slot (LAN-only, cellular slot never applies). */
fun HistoryDbHelper.nativeHideWatched(): Boolean = runBlocking { playerPreferences().hideWatchedVideosFromHome.first() }

fun HistoryDbHelper.nativeSetHideWatched(enabled: Boolean) {
    runBlocking { playerPreferences().setHideWatchedVideosFromHome(enabled) }
}

fun HistoryDbHelper.nativeHideShorts(): Boolean = runBlocking { !playerPreferences().shortsContentEnabled.first() }

fun HistoryDbHelper.nativeSetHideShorts(hide: Boolean) {
    runBlocking { playerPreferences().setShortsContentEnabled(!hide) }
}

fun HistoryDbHelper.nativeVideoQuality(): String = runBlocking { playerPreferences().defaultQualityWifi.first().label }
