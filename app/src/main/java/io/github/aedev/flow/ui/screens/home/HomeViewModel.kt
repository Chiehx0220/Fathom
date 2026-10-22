package io.github.aedev.flow.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.account.YouTubeHomeFeed
import io.github.aedev.flow.data.engagement.FeedInvalidationBus
import io.github.aedev.flow.data.feed.BlockedContentStore
import io.github.aedev.flow.data.feed.FeedExclusions
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.data.shorts.ShortsFeedRepository
import io.github.aedev.flow.data.shorts.queue.ShortsQueueHandoff
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject

/** Home feed: YouTube home plus the Bilibili lane, per source filter. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val subscriptionRepository: SubscriptionRepository,
        private val shortsRepository: ShortsFeedRepository,
        private val playerPreferences: PlayerPreferences,
        private val shortsQueueHandoff: ShortsQueueHandoff,
        private val viewHistory: ViewHistory,
        private val youTubeHome: YouTubeHomeFeed,
        private val blockedContent: BlockedContentStore,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        fun shortsShelfSource(
            shelf: List<Video>,
            tapped: Video,
        ) = shortsQueueHandoff.sourceForShelf(shelf, tapped)

        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> =
            _uiState
                .map(HomeUiState::withUniqueLazyContent)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), _uiState.value.withUniqueLazyContent())

        val contentSourceFilter: StateFlow<HomeContentSourceFilter> =
            playerPreferences.homeContentSourceFilterIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS))

        private val refreshFailureEvents = Channel<Unit>(Channel.CONFLATED)

        /** A refresh that failed while the old feed stayed on screen. */
        internal val refreshFailures: Flow<Unit> = refreshFailureEvents.receiveAsFlow()

        private val watchedVideoIds = MutableStateFlow<Set<String>>(emptySet())
        private val watchedShortIds = MutableStateFlow<Set<String>>(emptySet())
        private var isInitialized = false
        private var feedJob: Job? = null
        private var moreJob: Job? = null

        @Volatile
        private var lastVisibleVideoIndex = -1

        init {
            viewModelScope.launch { youTubeHome.signedIn.drop(1).collect { reloadFeed() } }
            loadFeed()
        }

        /** Persists the filter and reloads. */
        fun setContentSourceFilter(filter: HomeContentSourceFilter) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                playerPreferences.setHomeContentSourceFilter(filter)
                loadFeed(refresh = true)
            }
        }

        fun initialize(context: Context) {
            if (isInitialized) return
            isInitialized = true
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                combine(
                    viewHistory.getVideoHistoryFlow(),
                    playerPreferences.hideWatchedVideosFromHome,
                    playerPreferences.watchedThreshold,
                    playerPreferences.continueWatchingEnabled,
                    playerPreferences.hideWatchedShorts,
                ) { history, hideWatched, threshold, continueWatchingEnabled, hideWatchedShorts ->
                    filterHomeHistory(history, hideWatched, threshold, continueWatchingEnabled, hideWatchedShorts)
                }.collect { result ->
                    watchedVideoIds.value = result.watchedVideoIds
                    watchedShortIds.value = result.watchedShortIds
                    _uiState.update { state ->
                        state.copy(
                            videos = state.videos.filterWatched(result.watchedVideoIds),
                            shorts = state.shorts.filterWatched(result.watchedShortIds),
                            continueWatchingVideos = result.continueWatchingVideos,
                        )
                    }
                }
            }
            viewModelScope.launch { FeedInvalidationBus.events.collect(::onInvalidated) }
            viewModelScope.launch {
                playerPreferences.homeSubscriptionsEnabled
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { reloadFeed() }
            }
            viewModelScope.launch {
                playerPreferences.effectiveHomeShortsShelfEnabled.distinctUntilChanged().collect { enabled ->
                    if (!enabled) {
                        _uiState.update { it.copy(shorts = emptyList()) }
                    } else if (_uiState.value.shorts.isEmpty() && !_uiState.value.isLoading) {
                        reloadFeed()
                    }
                }
            }
        }

        fun onHomeVisible() = loadMore(lastVisibleVideoIndex)

        fun onHomeHidden() {
            moreJob?.cancel()
            _uiState.update { it.copy(isLoadingMore = false) }
        }

        fun onHomeViewportChanged(lastVisibleVideoIndex: Int) {
            this.lastVisibleVideoIndex = lastVisibleVideoIndex
            loadMore(lastVisibleVideoIndex)
        }

        fun retryLoadMore() {
            _uiState.update { it.copy(loadMoreFailed = false) }
            loadMore(lastVisibleVideoIndex)
        }

        fun retry() = loadFeed(refresh = true)

        /** User refresh; joins a running load. */
        fun refreshFeed() {
            if (feedJob?.isActive == true) {
                _uiState.update { it.copy(isRefreshing = true) }
                return
            }
            reloadFeed()
        }

        fun removeContinueWatchingEntry(videoId: String) {
            viewModelScope.launch { viewHistory.clearVideoHistory(videoId) }
        }

        private fun reloadFeed() {
            _uiState.update { it.copy(isRefreshing = true) }
            loadFeed(refresh = true)
        }

        private fun loadFeed(refresh: Boolean = false) {
            if (_uiState.value.isLoading && !refresh) return
            feedJob?.cancel()
            moreJob?.cancel()
            _uiState.update { it.copy(isLoading = true, isLoadingMore = false, loadMoreFailed = false, error = null) }
            feedJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    val filter = playerPreferences.homeContentSourceFilter.first()
                    val exclusions = exclusions()
                    val bilibili = launchBilibiliWave1Feeds(subscriptionRepository, appContext, filter)
                    val youTube = if (filter.allows(ServiceList.YouTube.serviceId)) youTubeHome.refresh() else Result.success(emptyList())
                    val lane = bilibili.await()
                    val videos =
                        (youTube.getOrDefault(emptyList()) + lane.viral + lane.subs)
                            .filterValid()
                            .filterNot(exclusions::hides)
                            .filterWatched(watchedVideoIds.value)
                            .forSourceFilter(filter)
                    if (videos.isEmpty() && youTube.isFailure) {
                        settleWithoutFeed(youTube.exceptionOrNull()?.message)
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            videos = videos,
                            isLoading = false,
                            isRefreshing = false,
                            hasMorePages = youTubeHome.hasMore,
                            error = null,
                            lastRefreshTime = System.currentTimeMillis(),
                        )
                    }
                    loadShortsShelf()
                }
        }

        private suspend fun loadShortsShelf() {
            if (!playerPreferences.effectiveHomeShortsShelfEnabled.first()) return
            val shelf =
                runCatching { shortsRepository.openFeed(null) }
                    .getOrDefault(emptyList())
                    .map { it.toVideo().copy(isShort = true) }
                    .filterWatched(watchedShortIds.value)
                    .take(SHORTS_SHELF_SIZE)
            if (shelf.isNotEmpty()) _uiState.update { it.copy(shorts = shelf) }
        }

        private fun loadMore(lastVisibleIndex: Int) {
            val state = _uiState.value
            if (state.isLoading || state.isLoadingMore || state.loadMoreFailed || !state.hasMorePages) return
            if (state.videos.size - lastVisibleIndex > LOAD_AHEAD) return
            moreJob?.cancel()
            _uiState.update { it.copy(isLoadingMore = true) }
            moreJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    val exclusions = exclusions()
                    youTubeHome
                        .loadMore()
                        .onSuccess { more ->
                            _uiState.update { current ->
                                val known = current.videos.mapTo(HashSet()) { it.id }
                                val fresh =
                                    more
                                        .filterValid()
                                        .filterNot(exclusions::hides)
                                        .filterWatched(watchedVideoIds.value)
                                        .filter { known.add(it.id) }
                                current.copy(videos = current.videos + fresh, isLoadingMore = false, hasMorePages = youTubeHome.hasMore)
                            }
                        }.onFailure { _uiState.update { it.copy(isLoadingMore = false, loadMoreFailed = true) } }
                }
        }

        private fun settleWithoutFeed(error: String?) {
            var keptFeed = false
            _uiState.update {
                keptFeed = it.videos.isNotEmpty()
                if (keptFeed) {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                    )
                } else {
                    it.copy(isLoading = false, isRefreshing = false, hasMorePages = false, error = error)
                }
            }
            if (keptFeed) refreshFailureEvents.trySend(Unit)
        }

        private suspend fun exclusions(): FeedExclusions {
            val showSubscriptions = playerPreferences.homeSubscriptionsEnabled.first()
            val subscriptions = runCatching { subscriptionRepository.getAllSubscriptionIds() }.getOrDefault(emptySet())
            return runCatching { blockedContent.exclusions() }
                .getOrDefault(FeedExclusions.NONE)
                .hidingChannels(if (showSubscriptions) emptySet() else subscriptions)
        }

        private fun onInvalidated(event: FeedInvalidationBus.Event) {
            when (event) {
                is FeedInvalidationBus.Event.ChannelBlocked -> dropFromFeed(channelId = event.channelId, videoId = event.videoId)
                is FeedInvalidationBus.Event.ChannelUnsubscribed -> dropFromFeed(channelId = event.channelId)
                is FeedInvalidationBus.Event.NotInterested -> dropFromFeed(videoId = event.videoId)
                is FeedInvalidationBus.Event.MarkedWatched -> dropFromFeed(videoId = event.videoId)
            }
        }

        private fun dropFromFeed(
            channelId: String? = null,
            videoId: String? = null,
        ) {
            _uiState.update { state ->
                fun Video.dropped() = id == videoId || (channelId != null && this.channelId == channelId)
                state.copy(videos = state.videos.filterNot { it.dropped() }, shorts = state.shorts.filterNot { it.dropped() })
            }
            channelId?.let(shortsRepository::evictChannel)
        }

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
            const val LOAD_AHEAD = 6
            const val SHORTS_SHELF_SIZE = 12
        }
    }
