package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import org.schabi.newpipe.extractor.ServiceList

/** Two pills: service for search/paste-link resolution. */
@Composable
fun SearchServiceSwitcher(
    selectedServiceId: Int,
    onServiceSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options =
        listOf(
            ServiceList.YouTube.serviceId to "YouTube",
            BILIBILI_SERVICE_ID to "Bilibili",
        )
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
