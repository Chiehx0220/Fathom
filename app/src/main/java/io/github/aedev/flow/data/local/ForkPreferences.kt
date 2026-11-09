package io.github.aedev.flow.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Fork preferences on the player-preferences store; outside the upstream class (clean merges).
private object ForkKeys {
    val HOME_CONTENT_SOURCE_FILTER = stringPreferencesKey("home_content_source_filter")
    val EXPLORE_SOURCE = stringPreferencesKey("explore_source")
}

val PlayerPreferences.homeContentSourceFilter: Flow<HomeContentSourceFilter>
    get() = context.playerPreferencesDataStore.data.map { HomeContentSourceFilter.fromStoredName(it[ForkKeys.HOME_CONTENT_SOURCE_FILTER]) }

suspend fun PlayerPreferences.setHomeContentSourceFilter(filter: HomeContentSourceFilter) {
    context.playerPreferencesDataStore.edit { it[ForkKeys.HOME_CONTENT_SOURCE_FILTER] = filter.name }
}

/** Explore tab source: YouTube or Bilibili only. */
val PlayerPreferences.exploreSource: Flow<HomeContentSourceFilter>
    get() =
        context.playerPreferencesDataStore.data.map {
            HomeContentSourceFilter.fromStoredName(it[ForkKeys.EXPLORE_SOURCE]).takeIf { source ->
                source == HomeContentSourceFilter.BILIBILI
            } ?: HomeContentSourceFilter.YOUTUBE
        }

suspend fun PlayerPreferences.setExploreSource(source: HomeContentSourceFilter) {
    context.playerPreferencesDataStore.edit { it[ForkKeys.EXPLORE_SOURCE] = source.name }
}
