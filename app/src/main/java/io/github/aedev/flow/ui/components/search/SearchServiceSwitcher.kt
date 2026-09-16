package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.extractor.ServiceList

/** Two pills to pick which streaming service search/paste-link resolution runs against. */
@Composable
fun SearchServiceSwitcher(
    selectedServiceId: Int,
    onServiceSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        listOf(
            ServiceList.YouTube.serviceId to "YouTube",
            ServiceList.BiliBili.serviceId to "Bilibili",
        )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (serviceId, label) ->
            FilterChip(
                selected = selectedServiceId == serviceId,
                onClick = { onServiceSelected(serviceId) },
                label = { Text(label) },
            )
        }
    }
}
