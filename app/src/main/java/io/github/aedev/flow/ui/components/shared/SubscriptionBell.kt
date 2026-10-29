package io.github.aedev.flow.ui.components.shared

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val BELL_SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** Why a bell that is on cannot deliver anything. */
internal enum class BellAlertBlock {
    /** The system blocks Flow's notifications, or the Android 13 permission was never granted. */
    System,

    /** Flow's own Notifications or New videos switch is off. */
    App,
}

internal fun bellAlertBlock(
    systemAllows: Boolean,
    notificationsOn: Boolean,
    newVideosOn: Boolean,
): BellAlertBlock? =
    when {
        !systemAllows -> BellAlertBlock.System
        !notificationsOn || !newVideosOn -> BellAlertBlock.App
        else -> null
    }

/** The bell on every subscribe button that has no screen of its own behind it. */
@HiltViewModel
class SubscriptionBellViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val subscriptionRepository: SubscriptionRepository,
        private val playerPreferences: PlayerPreferences,
    ) : ViewModel() {
        val notifyingChannelIds: StateFlow<Set<String>> =
            subscriptionRepository
                .notifyingChannelIds()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(BELL_SUBSCRIPTION_TIMEOUT_MS), emptySet())

        fun setNotifications(
            channelId: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionRepository.updateNotificationState(channelId, enabled)
            }
        }

        internal val blockedBells: Flow<BellAlertBlock> =
            subscriptionRepository.notificationsTurnedOn.mapNotNull {
                bellAlertBlock(
                    systemAllows = NotificationManagerCompat.from(appContext).areNotificationsEnabled(),
                    notificationsOn = playerPreferences.notificationsEnabled.first(),
                    newVideosOn = playerPreferences.notifNewVideosEnabled.first(),
                )
            }

        fun turnOnNewVideoAlerts() {
            viewModelScope.launch {
                playerPreferences.setNotificationsEnabled(true)
                playerPreferences.setNotifNewVideosEnabled(true)
            }
        }
    }

/** The activity's [SubscriptionBellViewModel], so every card, sheet and Short shares one. */
@Composable
fun sharedSubscriptionBellViewModel(): SubscriptionBellViewModel {
    val activity = LocalContext.current as? ComponentActivity
    return if (activity != null) hiltViewModel(activity) else hiltViewModel()
}

/**
 * Says so when a bell is turned on but nothing can arrive, with an action that fixes it (#1467).
 * Mounted once in the shell: it reacts to taps only, never to what is already stored.
 */
@Composable
fun SubscriptionBellAlerts(
    snackbarHostState: SnackbarHostState,
    viewModel: SubscriptionBellViewModel = sharedSubscriptionBellViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val message = stringResource(R.string.subscription_bell_alerts_off)
    val action = stringResource(R.string.subscription_bell_alerts_turn_on)
    LaunchedEffect(viewModel, snackbarHostState, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.blockedBells.collect { block ->
                val result = snackbarHostState.showSnackbar(message, action, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.turnOnNewVideoAlerts()
                    if (block == BellAlertBlock.System) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                }
            }
        }
    }
}
