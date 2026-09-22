package io.github.aedev.flow.ui.components.shared

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.BilibiliDownload
import kotlinx.coroutines.launch

/** Quality picker for a Bilibili video. The YouTube download dialog only knows YouTube's stream lists. */
@Composable
fun BilibiliDownloadDialog(
    video: Video,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var heights by remember { mutableStateOf<List<Int>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(video.id) {
        runCatching { BilibiliDownload.availableHeights(context, video) }
            .onSuccess { heights = it }
            .onFailure { error = it.message.orEmpty() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_video)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = heights
                when {
                    error != null ->
                        Text(
                            stringResource(R.string.ui_download_start_failed, error.orEmpty()),
                            color = MaterialTheme.colorScheme.error,
                        )
                    list == null ->
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    else -> {
                        Text(
                            stringResource(R.string.select_quality),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 360.dp),
                        ) {
                            items(list) { height ->
                                FilledTonalButton(
                                    onClick = {
                                        // Dismissing first would cancel this scope, so the dialog closes once the download has started.
                                        scope.launch {
                                            val message =
                                                runCatching { BilibiliDownload.start(context, video, height) }.fold(
                                                    onSuccess = { context.getString(R.string.toast_download_started, video.title) },
                                                    onFailure = { context.getString(R.string.ui_download_start_failed, it.message.orEmpty()) },
                                                )
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("${height}p")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
