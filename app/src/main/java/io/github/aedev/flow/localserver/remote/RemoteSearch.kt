@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.aedev.flow.localserver.remote

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.HistoryDbHelper
import io.github.aedev.flow.localserver.nativeSearchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// How many past searches are looked through while typing, and how many of those are shown.
private const val HISTORY_LOOKBACK = 50
private const val MAX_SUGGESTIONS = 8

/**
 * Typing on a screen with no keyboard is the worst part of a remote, so there are three ways round it: type here,
 * tap something searched before, or speak. The page runs the search either way.
 */
@Composable
internal fun SearchField(send: (String) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    val voiceUnavailable = stringResource(R.string.remote_voice_unavailable)
    val voicePrompt = stringResource(R.string.remote_search_voice_prompt)

    // Read again whenever the field is entered, so what was just searched is in the list.
    LaunchedEffect(focused) {
        if (focused) history = withContext(Dispatchers.IO) { HistoryDbHelper.getInstance(context).nativeSearchHistory().take(HISTORY_LOOKBACK) }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        text = trimmed
        send("search:$trimmed")
        focus.clearFocus()
    }

    val voice =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(::search)
            }
        }
    val startVoice = {
        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
        try {
            voice.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, voiceUnavailable, Toast.LENGTH_SHORT).show()
        }
    }

    val suggestions =
        remember(history, text) {
            val typed = text.trim()
            history.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(MAX_SUGGESTIONS)
        }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                placeholder = { Text(stringResource(R.string.remote_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (text.isEmpty()) {
                        IconButton(onClick = startVoice) {
                            Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.remote_search_voice))
                        }
                    } else {
                        IconButton(onClick = { text = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remote_search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search(text) }),
            )
            // As tall as the field and separate from it, so the pill keeps its own shape.
            FilledIconButton(onClick = { search(text) }, enabled = text.isNotBlank(), modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.remote_search_submit))
            }
        }
        if (focused && suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions, key = { it }) { query ->
                    AssistChip(
                        onClick = { search(query) },
                        label = { Text(query, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }
    }
}
