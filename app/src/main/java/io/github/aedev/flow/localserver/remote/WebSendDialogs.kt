package io.github.aedev.flow.localserver.remote

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import kotlinx.coroutines.launch

/** Confirm → enable server → [send] → [WaitingForPageDialog]. */
@Composable
internal fun EnableServerAndSend(
    send: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sent by rememberSaveable { mutableStateOf(false) }
    if (sent) {
        WaitingForPageDialog(onDone)
        return
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.share_web_server_off_title)) },
        text = { Text(stringResource(R.string.share_web_server_off_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        enableLocalServer(context)
                        send()
                        sent = true
                    }
                },
            ) { Text(stringResource(R.string.share_web_enable_send)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
internal fun WaitingForPageDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_web_queued_title)) },
        text = { Text(stringResource(R.string.share_web_queued_body, webAddress())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}
