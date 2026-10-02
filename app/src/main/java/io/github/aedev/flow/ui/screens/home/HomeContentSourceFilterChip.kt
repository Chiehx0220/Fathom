package io.github.aedev.flow.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.ui.components.shared.FlowDropdownFilterChip

private fun HomeContentSourceFilter.label(mixLabel: String): String =
    when (this) {
        HomeContentSourceFilter.MIX -> mixLabel
        HomeContentSourceFilter.YOUTUBE -> "YouTube"
        HomeContentSourceFilter.BILIBILI -> "Bilibili"
    }

/**
 * Dropdown chip picking which service a feed shows. Home applies it to its fresh-subs and
 * recommendation lanes; Subscriptions and Search are unaffected - see HomeContentSourceFilter.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeContentSourceFilterChip(
    selected: HomeContentSourceFilter,
    onSelect: (HomeContentSourceFilter) -> Unit,
    modifier: Modifier = Modifier,
    options: List<HomeContentSourceFilter> = HomeContentSourceFilter.entries,
) {
    var expanded by remember { mutableStateOf(false) }
    val mixLabel = stringResource(R.string.home_content_filter_mix)
    val description = stringResource(R.string.home_content_filter_description) + ", " + selected.label(mixLabel)

    Box(modifier = modifier) {
        FlowDropdownFilterChip(
            label = selected.label(mixLabel),
            selected = true,
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    selectedTrailingIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
        )
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        selected = option == selected,
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                        text = { Text(text = option.label(mixLabel)) },
                        shapes = MenuDefaults.itemShape(index = index, count = options.size),
                        selectedLeadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}
