package io.github.aedev.flow.localserver.remote

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.Pairing
import io.github.aedev.flow.localserver.ServerService

/** Nothing paired: instruction + address to open. */
@Composable
internal fun NotConnected() {
    val serverRunning by ServerService.runningState.collectAsState()
    val address =
        remember(serverRunning) {
            if (serverRunning) ServerService.getLocalIpAddress()?.let { "http://$it:${ServerService.PORT}" } else null
        }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val pairingCode by remember { Pairing.of(context) }.code.collectAsState()
    val copiedMessage = stringResource(R.string.remote_address_copied)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(104.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.LinkOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.remote_not_connected), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            ConnectStep(1, stringResource(R.string.remote_connect_step_open))
            if (address != null) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(address, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(address))
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.remote_copy_address))
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.remote_server_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (address != null) {
                ConnectStep(2, stringResource(R.string.remote_connect_step_pair))
                PairingCodeCells(
                    code = pairingCode,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            ConnectStep(if (address != null) 3 else 2, stringResource(R.string.remote_connect_step_cast, CAST_MARK), castIcon = true)
        }
    }
}

/** Placeholder replaced by the cast icon inside the step text. */
private const val CAST_MARK = "\u0001"
private const val CAST_ID = "cast"

@Composable
private fun ConnectStep(
    number: Int,
    text: String,
    castIcon: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        if (castIcon && CAST_MARK in text) {
            val annotated =
                buildAnnotatedString {
                    append(text.substringBefore(CAST_MARK))
                    appendInlineContent(CAST_ID, "cast")
                    append(text.substringAfter(CAST_MARK))
                }
            val inline =
                mapOf(
                    CAST_ID to
                        InlineTextContent(Placeholder(24.sp, 24.sp, PlaceholderVerticalAlign.TextCenter)) {
                            Icon(Icons.Default.Cast, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                )
            Text(annotated, style = MaterialTheme.typography.bodyLarge, inlineContent = inline, modifier = Modifier.weight(1f))
        } else {
            Text(text.replace(CAST_MARK, ""), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}
