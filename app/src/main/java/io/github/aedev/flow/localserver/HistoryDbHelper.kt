package io.github.aedev.flow.localserver

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.data.local.safePreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * "mix" vs "subs" home-feed source - Local Server's one setting with no native equivalent (see
 * buildAndRankHomeFeed()/buildSubsOnlyFeed()). DataStore-backed, matching other app stores.
 */
private val Context.localServerDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "local_server_settings")
private val HOME_FEED_MODE_KEY = stringPreferencesKey("home_feed_mode")

class HistoryDbHelper private constructor(
    val appContext: Context,
) {
    companion object {
        @Volatile
        private var instance: HistoryDbHelper? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): HistoryDbHelper {
            var result = instance
            if (result == null) {
                result = HistoryDbHelper(context.applicationContext)
                instance = result
            }
            return result
        }
    }

    var homeFeedMode: String
        get() = runBlocking { appContext.localServerDataStore.data.first()[HOME_FEED_MODE_KEY] ?: "mix" }
        set(value) {
            runBlocking { appContext.localServerDataStore.edit { it[HOME_FEED_MODE_KEY] = value } }
        }
}
