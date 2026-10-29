package io.github.aedev.flow.ui.screens.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.profile.ProfileRepository
import io.github.aedev.flow.data.profile.SyncProfile
import io.github.aedev.flow.sync.SyncManager
import io.github.aedev.flow.sync.SyncState
import io.github.aedev.flow.sync.protocol.SyncRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Thin ViewModel over the singleton [SyncManager] (which survives config changes). */
@HiltViewModel
class SyncViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val manager: SyncManager,
        profiles: ProfileRepository,
    ) : ViewModel() {
        val state: StateFlow<SyncState> = manager.state

        val syncsHere: Boolean = SyncProfile.syncsHere(context)

        val syncProfileName: StateFlow<String> =
            profiles.state
                .map { SyncProfile.name(it, context) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

        fun host(
            role: SyncRole,
            collections: List<String>,
        ) = manager.host(role, collections)

        fun join(
            role: SyncRole,
            qrText: String,
            collections: List<String>,
        ) = manager.join(role, qrText, collections)

        fun extendPairingForManualShare(): String? = manager.extendPairingForManualShare()

        fun confirmSas(matches: Boolean) = manager.confirmSas(matches)

        fun confirmConsent(accepted: Boolean) = manager.confirmConsent(accepted)

        fun cancel() = manager.cancel()

        fun reset() = manager.reset()
    }
