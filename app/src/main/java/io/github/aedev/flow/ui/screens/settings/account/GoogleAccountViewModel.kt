package io.github.aedev.flow.ui.screens.settings.account

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.account.GoogleAccountStore
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.AccountInfo
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

@HiltViewModel
internal class GoogleAccountViewModel
    @Inject
    constructor(
        private val store: GoogleAccountStore,
    ) : SettingsViewModel() {
        val signedIn: StateFlow<Boolean> = store.account.map { it != null }.asState(false)

        val info: StateFlow<AccountInfo?> =
            store.account.mapLatest { if (it == null) null else YouTube.accountInfo().getOrNull() }.asState(null)

        val syncEnabled: StateFlow<Boolean> = store.syncEnabled.asState(false)

        fun setSyncEnabled(enabled: Boolean) {
            write { store.setSyncEnabled(enabled) }
        }

        fun signOut() {
            write { store.clear() }
        }
    }
