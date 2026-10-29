package io.github.aedev.flow.data.local

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import io.github.aedev.flow.data.profile.ProfileFiles
import io.github.aedev.flow.data.profile.StoreScope
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException

/**
 * Settings that belong to the device rather than to a profile: downloads, language, region, player controls,
 * storage and update checks. They used to live in player_preferences and flow_preferences; [DeviceKeysCopy] copies
 * them here once.
 */
internal val Context.devicePreferencesDataStore: DataStore<Preferences> by safePreferencesDataStore(
    name = "device_preferences",
    scope = StoreScope.DEVICE,
    produceMigrations = { context ->
        listOf(
            DeviceKeysCopy(
                listOf(
                    DeviceKeysCopy.Source(
                        store = context.playerPreferencesDataStore,
                        names = PlayerPreferences.DEVICE_KEY_NAMES,
                        file = ProfileFiles.preferences(context, StoreScope.PROFILE_SETTINGS, "player_preferences"),
                    ),
                    DeviceKeysCopy.Source(
                        store = context.dataStore,
                        names = LocalDataManager.DEVICE_KEY_NAMES,
                        file = ProfileFiles.preferences(context, StoreScope.PROFILE_SETTINGS, "flow_preferences"),
                    ),
                ),
            ),
        )
    },
)

/**
 * Copies the device keys that are set in each source into the device store, once. The sources keep their values,
 * so an older build still finds them; nothing in this build reads them there again.
 */
internal class DeviceKeysCopy(
    private val sources: List<Source>,
) : DataMigration<Preferences> {
    class Source(
        val store: DataStore<Preferences>,
        val names: Set<String>,
        val file: File,
    )

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[COPIED] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val copied = currentData.toMutablePreferences()
        for (source in sources) {
            val stored =
                source.store.data
                    .first()
                    .asMap()
            // The store reads back empty when its file can't be read; copying then would lose every value for good.
            if (stored.isEmpty() && source.file.length() > 0) throw IOException("${source.file.name} is unreadable, copying later")
            stored.forEach { (key, value) ->
                if (key.name in source.names && !copied.asMap().containsKey(key)) copied.putUnchecked(key, value)
            }
        }
        copied[COPIED] = true
        return copied.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    companion object {
        val COPIED = booleanPreferencesKey("device_keys_copied")
    }
}

@Suppress("UNCHECKED_CAST")
private fun MutablePreferences.putUnchecked(
    key: Preferences.Key<*>,
    value: Any,
) {
    this[key as Preferences.Key<Any>] = value
}
