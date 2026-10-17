package io.github.aedev.flow.localserver.remote

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.safePreferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.remoteHints by safePreferencesDataStore(name = "remote_hints")
private val VolumeKeysSeen = booleanPreferencesKey("volume_keys_seen")

/** One-time card: hardware volume keys control page volume. */
@Composable
internal fun VolumeKeysHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Default seen: no flash before DataStore loads.
    val seen by remember(context) { context.remoteHints.data.map { it[VolumeKeysSeen] ?: false } }.collectAsState(initial = true)
    if (seen) return
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.remote_volume_hint_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.remote_volume_hint_body), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { scope.launch { context.remoteHints.edit { it[VolumeKeysSeen] = true } } }) {
                        Text(stringResource(R.string.remote_hint_dismiss))
                    }
                }
            }
        }
    }
}
