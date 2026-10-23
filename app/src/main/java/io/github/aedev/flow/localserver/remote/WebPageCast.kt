package io.github.aedev.flow.localserver.remote

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.RemoteSession
import io.github.aedev.flow.localserver.ServerService
import io.github.aedev.flow.player.dlna.DlnaDevice
import io.github.aedev.flow.ui.components.videoplayer.DlnaDevicePickerDialog
import io.github.aedev.flow.widget.core.action.WidgetDeepLink

private const val WEB_PAGE_USN = "fathom:web-page"
private const val WEB_ADDRESS_USN = "fathom:web-address"
private const val SERVER_OFF_USN = "fathom:server-off"
private const val SETTINGS_ROUTE = "settings"

/** [DlnaDevicePickerDialog] with web page entries beside the DLNA devices. */
@Composable
internal fun CastPickerWithWebPage(
    devices: List<DlnaDevice>,
    isDiscovering: Boolean,
    isCasting: Boolean,
    videoTitle: String,
    onDeviceSelected: (DlnaDevice) -> Unit,
    onStopCasting: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lock by RemoteSession.remoteLock.collectAsStateWithLifecycle()
    val serverRunning by ServerService.runningState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val connected = stringResource(R.string.cast_web_page, lock.clientIp.orEmpty())
    val address = "http://${ServerService.getLocalIpAddress() ?: "127.0.0.1"}:${ServerService.PORT}"
    val unconnected = stringResource(R.string.cast_web_address, address)
    val serverOff = stringResource(R.string.cast_web_server_off)
    val page =
        remember(lock.locked, serverRunning, connected, unconnected, serverOff, devices.isEmpty()) {
            when {
                lock.locked -> listOf(DlnaDevice(connected, "", WEB_PAGE_USN))
                serverRunning -> listOf(DlnaDevice(unconnected, "", WEB_ADDRESS_USN))
                devices.isEmpty() -> listOf(DlnaDevice(serverOff, "", SERVER_OFF_USN))
                else -> emptyList()
            }
        }
    DlnaDevicePickerDialog(
        devices = devices + page,
        isDiscovering = isDiscovering,
        isCasting = isCasting,
        videoTitle = videoTitle,
        onDeviceSelected = { device ->
            when (device.usn) {
                WEB_PAGE_USN -> {
                    sendPhoneVideoToPage()
                    onDismiss()
                }

                SERVER_OFF_USN -> {
                    context.startActivity(WidgetDeepLink.openRoute(context, SETTINGS_ROUTE))
                    onDismiss()
                }

                WEB_ADDRESS_USN -> {
                    clipboard.setText(AnnotatedString(address))
                    Toast.makeText(context, R.string.cast_web_address_copied, Toast.LENGTH_SHORT).show()
                }

                else -> {
                    onDeviceSelected(device)
                }
            }
        },
        onStopCasting = onStopCasting,
        onDismiss = onDismiss,
    )
}
