package io.github.aedev.flow.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.HomeContentSourceFilter

private fun HomeContentSourceFilter.label(mixLabel: String): String =
    when (this) {
        HomeContentSourceFilter.MIX -> mixLabel
        HomeContentSourceFilter.YOUTUBE -> "YouTube"
        HomeContentSourceFilter.BILIBILI -> "Bilibili"
    }

/**
 * Compact dropdown chip picking which service Home's fresh-subs/recommendation lanes show.
 * Subscriptions and Search are unaffected - see HomeContentSourceFilter.
 */
@Composable
fun HomeContentSourceFilterChip(
    selected: HomeContentSourceFilter,
    onSelect: (HomeContentSourceFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val mixLabel = stringResource(R.string.home_content_filter_mix)

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected.label(mixLabel),
                    style = MaterialTheme.typography.titleSmall,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.home_content_filter_description),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HomeContentSourceFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label(mixLabel)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
