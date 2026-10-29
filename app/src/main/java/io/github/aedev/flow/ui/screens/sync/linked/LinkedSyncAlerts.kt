package io.github.aedev.flow.ui.screens.sync.linked

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.sync2.service.LinkedSync
import javax.inject.Inject

@HiltViewModel
internal class LinkedSyncAlertsViewModel
    @Inject
    constructor(
        sync: LinkedSync,
    ) : ViewModel() {
        val received = sync.received
    }

/** Says briefly what a Sync now brought in, while the app is on screen. */
@Composable
fun LinkedSyncAlerts(snackbarHostState: SnackbarHostState) {
    val viewModel = hiltViewModel<LinkedSyncAlertsViewModel>()
    val resources = LocalContext.current.resources
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, snackbarHostState, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.received.collect {
                snackbarHostState.showSnackbar(resources.getQuantityString(R.plurals.linked_received, it.changes, it.changes, it.from))
            }
        }
    }
}
