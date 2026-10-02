package io.github.aedev.flow.ui.screens.categories

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.BilibiliVideoMapper
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BilibiliExploreTab(
    @StringRes val labelRes: Int,
) {
    POPULAR(R.string.explore_bilibili_popular),
    LIVE(R.string.category_live),
}

data class BilibiliExploreState(
    val tab: BilibiliExploreTab = BilibiliExploreTab.POPULAR,
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class BilibiliExploreViewModel
    @Inject
    constructor(
        private val bilibili: BilibiliApi,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow(BilibiliExploreState())
        val state: StateFlow<BilibiliExploreState> = _state.asStateFlow()

        private val loaded = HashMap<BilibiliExploreTab, List<Video>>()
        private var loadJob: Job? = null

        init {
            load(BilibiliExploreTab.POPULAR)
        }

        fun select(tab: BilibiliExploreTab) {
            if (tab != _state.value.tab) load(tab)
        }

        fun refresh() {
            val tab = _state.value.tab
            loaded.remove(tab)
            load(tab)
        }

        private fun load(tab: BilibiliExploreTab) {
            loadJob?.cancel()
            loaded[tab]?.let { cached ->
                _state.value = BilibiliExploreState(tab = tab, videos = cached, isLoading = false)
                return
            }
            _state.value = BilibiliExploreState(tab = tab)
            loadJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    val videos =
                        try {
                            fetch(tab)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            emptyList()
                        }
                    if (videos.isNotEmpty()) loaded[tab] = videos
                    _state.update {
                        it.copy(
                            videos = videos,
                            isLoading = false,
                            error = if (videos.isEmpty()) context.getString(R.string.error_failed_to_load_videos) else null,
                        )
                    }
                }
        }

        private suspend fun fetch(tab: BilibiliExploreTab): List<Video> =
            when (tab) {
                BilibiliExploreTab.POPULAR ->
                    coroutineScope {
                        (1..POPULAR_PAGES)
                            .map { page -> async { runCatching { bilibili.popular(page) }.getOrDefault(emptyList()) } }
                            .awaitAll()
                            .flatten()
                            .distinctBy { it.bvid }
                            .map(BilibiliVideoMapper::videoFromRelated)
                    }

                BilibiliExploreTab.LIVE -> bilibili.recommendedLives().map(BilibiliVideoMapper::videoFromLiveItem)
            }

        private companion object {
            const val POPULAR_PAGES = 3
        }
    }
