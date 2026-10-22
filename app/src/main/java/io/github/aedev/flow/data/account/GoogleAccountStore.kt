package io.github.aedev.flow.data.account

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.KeystoreSecretBox
import io.github.aedev.flow.data.local.safePreferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.googleAccountDataStore by safePreferencesDataStore(name = "google_account")

data class GoogleAccount(
    val cookie: String,
    val dataSyncId: String?,
)

/** The cookie is a bearer credential: Keystore-sealed, in a store the backup rules do not list. */
@Singleton
class GoogleAccountStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val cookieKey = stringPreferencesKey("cookie")
        private val dataSyncIdKey = stringPreferencesKey("data_sync_id")
        private val syncKey = booleanPreferencesKey("sync_to_account")

        val account: Flow<GoogleAccount?> =
            context.googleAccountDataStore.data
                .map { it.account() }
                .flowOn(Dispatchers.IO)

        /** Opt-in; off writes nothing to the account. */
        val syncEnabled: Flow<Boolean> =
            context.googleAccountDataStore.data
                .map { it[syncKey] ?: false }
                .flowOn(Dispatchers.IO)

        suspend fun setSyncEnabled(enabled: Boolean) {
            context.googleAccountDataStore.edit { it[syncKey] = enabled }
        }

        suspend fun save(account: GoogleAccount) {
            val sealed = withContext(Dispatchers.IO) { KeystoreSecretBox.seal(account.cookie) }
            context.googleAccountDataStore.edit {
                it[cookieKey] = sealed
                if (account.dataSyncId.isNullOrBlank()) it.remove(dataSyncIdKey) else it[dataSyncIdKey] = account.dataSyncId
            }
        }

        suspend fun clear() {
            context.googleAccountDataStore.edit { it.clear() }
        }

        private fun Preferences.account(): GoogleAccount? {
            val cookie = KeystoreSecretBox.open(this[cookieKey]).takeIf { GoogleCookie.isSignedIn(it) } ?: return null
            return GoogleAccount(cookie, this[dataSyncIdKey])
        }
    }
