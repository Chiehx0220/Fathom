package io.github.aedev.flow.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.categories.CategoryChartGrid
import io.github.aedev.flow.ui.components.rememberFeedGridLayout
import io.github.aedev.flow.ui.components.shared.FeedGridSkeleton
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.FlowFilterChip

/** Explore with Bilibili as the source: its own tabs over the same grid the YouTube tabs use. */
@Composable
internal fun BilibiliExploreContent(
    onVideoClick: (Video) -> Unit,
    columnPreference: HomeFeedColumns,
    isListView: Boolean,
    sourceChip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BilibiliExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error = state.error

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = TabRowPadding, vertical = TabRowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(TabSpacing),
        ) {
            sourceChip()
            BilibiliExploreTab.entries.forEach { tab ->
                FlowFilterChip(
                    label = stringResource(tab.labelRes),
                    selected = state.tab == tab,
                    onClick = { viewModel.select(tab) },
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val feedLayout = rememberFeedGridLayout(maxWidth, columnPreference)
            when {
                state.isLoading -> {
                    FeedGridSkeleton(layout = feedLayout, listMode = isListView)
                }

                error != null -> {
                    FlowErrorState(error = error, onRetry = viewModel::refresh)
                }

                else -> {
                    key(state.tab) {
                        CategoryChartGrid(
                            entries = state.videos,
                            gridState = rememberLazyGridState(),
                            feedLayout = feedLayout,
                            isListView = isListView,
                            onVideoClick = onVideoClick,
                        )
                    }
                }
            }
        }
    }
}

private val TabRowPadding = 12.dp
private val TabRowVerticalPadding = 4.dp
private val TabSpacing = 8.dp
