package io.github.aedev.flow.ui.screens.home

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.ui.TabScrollEventBus
import io.github.aedev.flow.ui.components.FeedGridLayout
import io.github.aedev.flow.ui.components.shared.FeedGridSkeleton
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val SKELETON_CARD_COUNT = 12

/** Main feed scroll position, session-scoped. */
@Stable
internal class HomeGridStates(
    val feed: LazyGridState,
)

@Composable
internal fun rememberHomeGridStates(): HomeGridStates {
    val feed = rememberLazyGridState()
    return remember(feed) { HomeGridStates(feed) }
}

/** Visibility refresh, viewport-driven load-more and tab reselect (scroll to top, then refresh); phone and TV. */
@Composable
internal fun HomeFeedEffects(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    gridStates: HomeGridStates,
    homeFeedEnabled: Boolean,
    refreshHomeOnReselect: Boolean,
) {
    val gridState = gridStates.feed

    LifecycleStartEffect(viewModel, homeFeedEnabled) {
        if (homeFeedEnabled) {
            viewModel.onHomeVisible()
        } else {
            viewModel.onHomeHidden()
        }
        onStopOrDispose { viewModel.onHomeHidden() }
    }

    val videoIndexById =
        remember(uiState.videos) {
            buildMap(uiState.videos.size) {
                uiState.videos.forEachIndexed { index, video -> put(video.id, index) }
            }
        }
    // Pairing the index with the scroll state matters: a user parked on the last item keeps the
    // same index, so an index-only flow can never re-arm a prefetch that came back empty. Each
    // further scroll attempt toggles isScrollInProgress and gives the feed another chance.
    LaunchedEffect(gridState, videoIndexById) {
        snapshotFlow {
            var lastVisibleVideoIndex = -1
            gridState.layoutInfo.visibleItemsInfo.forEach { item ->
                val index = videoIndexById[item.key as? String] ?: return@forEach
                if (index > lastVisibleVideoIndex) lastVisibleVideoIndex = index
            }
            lastVisibleVideoIndex to gridState.isScrollInProgress
        }.distinctUntilChanged()
            .collect { (lastVisibleVideoIndex, _) ->
                viewModel.onHomeViewportChanged(lastVisibleVideoIndex)
            }
    }

    LaunchedEffect(refreshHomeOnReselect) {
        TabScrollEventBus.scrollToTopEvents
            .filter { it == "home" }
            .collectLatest {
                // Down the feed a reselect only scrolls back up; at the top it refreshes (#1171).
                val atTop = gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
                if (!atTop) {
                    gridState.animateScrollToItem(0)
                } else if (refreshHomeOnReselect) {
                    viewModel.refreshFeed()
                }
            }
    }
}

/** Loading, error and loaded states of the feed; [showShorts] false omits the Shorts shelf (TV). */
@Composable
internal fun HomeFeedBody(
    viewModel: HomeViewModel,
    uiState: HomeUiState,
    gridStates: HomeGridStates,
    feedLayout: FeedGridLayout,
    isListView: Boolean,
    homeFeedEnabled: Boolean,
    onVideoClick: (Video) -> Unit,
    onPlayMix: (videos: List<Video>, title: String) -> Unit,
    onShortClick: (ShortsQueueSource) -> Unit,
    onNavigateToHistory: () -> Unit,
    onOpenShortsFeed: () -> Unit,
    showShorts: Boolean = true,
) {
    when {
        !homeFeedEnabled -> {
            HomeFeedDisabledState(modifier = Modifier.fillMaxSize())
        }

        uiState.isLoading && uiState.videos.isEmpty() -> {
            FeedGridSkeleton(
                layout = feedLayout,
                listMode = isListView,
                stackedInset = !feedLayout.cardsBleed,
                placeholderCount = SKELETON_CARD_COUNT,
                compactRowSpacing = if (isListView) 0.dp else feedLayout.cardSpacing,
            )
        }

        uiState.error != null && uiState.videos.isEmpty() -> {
            FlowErrorState(
                error = uiState.error ?: stringResource(R.string.error_occurred),
                onRetry = { viewModel.retry() },
            )
        }

        else -> {
            ReportDrawnWhen {
                uiState.videos.isNotEmpty() ||
                    uiState.shorts.isNotEmpty() ||
                    uiState.continueWatchingVideos.isNotEmpty()
            }

            HomeFeedGrid(
                uiState = uiState,
                feedLayout = feedLayout,
                isListView = isListView,
                gridState = gridStates.feed,
                onVideoClick = onVideoClick,
                onContinueWatchingClick = { entry -> onVideoClick(entry.toResumeVideo()) },
                onContinueWatchingRemove = viewModel::removeContinueWatchingEntry,
                onShortClick = { shelf, tapped ->
                    onShortClick(viewModel.shortsShelfSource(shelf, tapped))
                },
                onSeeAllHistory = onNavigateToHistory,
                onOpenShortsFeed = onOpenShortsFeed,
                onRefresh = { viewModel.refreshFeed() },
                onRetryLoadMore = viewModel::retryLoadMore,
                // Since v5 the feed can end at a few dozen cards; a list that just stops reads as broken (#1317).
                footerMinVideos = 0,
                showShorts = showShorts,
            )
        }
    }
}
