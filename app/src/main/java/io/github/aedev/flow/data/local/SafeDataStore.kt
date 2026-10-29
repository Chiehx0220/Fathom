package io.github.aedev.flow.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.github.aedev.flow.data.profile.ProfileFiles
import io.github.aedev.flow.data.profile.StoreScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private const val SAFE_DATA_STORE_TAG = "SafeDataStore"
private const val READ_RETRY_ATTEMPTS = 3
private const val READ_RETRY_DELAY_MS = 100L

/**
 * A preferences DataStore whose file depends on [scope] and the profile this process runs as (see [ProfileFiles]).
 * The scope has no default so every store is placed deliberately; StoreScopeGuardTest checks it against the
 * inventory.
 */
fun safePreferencesDataStore(
    name: String,
    scope: StoreScope,
    produceMigrations: (Context) -> List<DataMigration<Preferences>> = { emptyList() },
): ReadOnlyProperty<Context, DataStore<Preferences>> {
    var wrapped: DataStore<Preferences>? = null
    return object : ReadOnlyProperty<Context, DataStore<Preferences>> {
        override fun getValue(
            thisRef: Context,
            property: KProperty<*>,
        ): DataStore<Preferences> =
            wrapped ?: synchronized(this) {
                wrapped ?: ReadSafeDataStore(name, create(thisRef.applicationContext, name, scope, produceMigrations)).also { wrapped = it }
            }
    }
}

private fun create(
    context: Context,
    name: String,
    scope: StoreScope,
    produceMigrations: (Context) -> List<DataMigration<Preferences>>,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler =
            ReplaceFileCorruptionHandler { corruption ->
                Log.w(SAFE_DATA_STORE_TAG, "Resetting corrupted preferences DataStore: $name", corruption)
                emptyPreferences()
            },
        migrations = produceMigrations(context),
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { ProfileFiles.preferences(context, scope, name) },
    )

internal class ReadSafeDataStore(
    private val name: String,
    private val delegate: DataStore<Preferences>,
    private val retryDelayMs: Long = READ_RETRY_DELAY_MS,
) : DataStore<Preferences> {
    // A failed read must not crash the collector. Retrying first keeps observers subscribed to
    // later writes; only a read that keeps failing falls back to defaults and ends the stream.
    override val data: Flow<Preferences> =
        delegate.data
            .retryWhen { cause, attempt ->
                val retry = cause is IOException && attempt < READ_RETRY_ATTEMPTS
                if (retry) delay(retryDelayMs * (attempt + 1))
                retry
            }.catch { cause ->
                if (cause !is IOException) throw cause
                Log.w(SAFE_DATA_STORE_TAG, "Reading preferences DataStore failed, using defaults: $name", cause)
                emit(emptyPreferences())
            }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = delegate.updateData(transform)
}
