package io.github.aedev.flow.ui

import android.content.Context
import android.net.Uri
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import io.github.aedev.flow.R
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.utils.NetworkConnectivityObserver
import kotlinx.coroutines.delay

@Composable
fun HandleDeepLinks(
    pendingDeeplink: PendingDeeplink?,
    navController: NavHostController,
    onDeeplinkConsumed: () -> Unit,
) {
    LaunchedEffect(pendingDeeplink) {
        if (pendingDeeplink != null) {
            val (videoId, serviceId, isShort) = pendingDeeplink
            val maxAttempts = 30
            var navigated = false
            for (attempt in 1..maxAttempts) {
                delay(100L)
                try {
                    if (navController.currentDestination != null) {
                        if (isShort) {
                            val src = Uri.encode(ShortsQueueSource.SeededFeed(videoId).encode())
                            navController.navigate("shorts?src=$src") {
                                launchSingleTop = true
                            }
                        } else {
                            // Route through navigateToPlayer (PlayerNavigation.kt), not a hand-built
                            // "player/$id?serviceId=$id" string: a raw Bilibili id can itself contain
                            // "?p=1", which would inject a second "?" and make the route's own
                            // "?serviceId=" query silently fail to parse, falling back to YouTube.
                            navController.navigateToPlayer(videoId, serviceId)
                        }
                        navigated = true
                        break
                    }
                } catch (e: Exception) {
                    android.util.Log.w(
                        "HandleDeepLinks",
                        "Navigation attempt $attempt failed for $videoId: ${e.message}",
                    )
                }
            }
            if (!navigated) {
                android.util.Log.e(
                    "HandleDeepLinks",
                    "Navigation failed after $maxAttempts attempts for: $videoId",
                )
            }
            onDeeplinkConsumed()
        }
    }
}

private const val OFFLINE_NOTICE_DELAY_MS = 3_000L

@Composable
fun OfflineMonitor(
    context: Context,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    currentRoute: State<String>,
) {
    val connectivity = remember(context) { NetworkConnectivityObserver(context) }
    val isConnected by remember(connectivity) { connectivity.observeConnectivity() }
        .collectAsStateWithLifecycle(initialValue = true)
    val route = currentRoute.value

    LaunchedEffect(isConnected, route) {
        if (isConnected) return@LaunchedEffect

        val isSafeRoute =
            route == "downloads" ||
                route.startsWith("player") ||
                route.startsWith("musicPlayer") ||
                route == "settings"
        if (isSafeRoute) return@LaunchedEffect

        delay(OFFLINE_NOTICE_DELAY_MS)

        val result =
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.error_no_internet_found),
                actionLabel = context.getString(R.string.downloads_title),
                duration = SnackbarDuration.Short,
            )
        if (result == SnackbarResult.ActionPerformed) {
            navController.navigate("downloads") {
                launchSingleTop = true
            }
        }
    }
}
