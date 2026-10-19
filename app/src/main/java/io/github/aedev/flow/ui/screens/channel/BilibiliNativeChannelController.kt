package io.github.aedev.flow.ui.screens.channel

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliChannelInfo
import io.github.aedev.flow.bilibili.BilibiliChannelSort
import io.github.aedev.flow.data.paging.BilibiliChannelPlaylistsPagingSource
import io.github.aedev.flow.data.paging.BilibiliChannelVideosPagingSource
import io.github.aedev.flow.innertube.pages.channel.ChannelFilterGroup
import io.github.aedev.flow.innertube.pages.channel.ChannelFilterOption
import io.github.aedev.flow.innertube.pages.channel.ChannelHeader
import io.github.aedev.flow.innertube.pages.channel.ChannelTabDescriptor
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Bilibili channel header + tabs via native client ([BilibiliApi]). */
internal class BilibiliNativeChannelController(
    private val scope: CoroutineScope,
    apiProvider: () -> BilibiliApi,
) : ChannelTabSource {
    private val api by lazy(apiProvider)

    private val _states = MutableStateFlow<Map<ChannelTabKind, ChannelTabState>>(emptyMap())
    override val states: StateFlow<Map<ChannelTabKind, ChannelTabState>> = _states.asStateFlow()

    private var info: BilibiliChannelInfo? = null
    private var sortLabels: Map<BilibiliChannelSort, String> = emptyMap()

    fun reset(
        info: BilibiliChannelInfo,
        videosLabel: String,
        playlistsLabel: String,
        sortLabels: Map<BilibiliChannelSort, String>,
    ): Pair<ChannelHeader, List<ChannelTabDescriptor>> {
        this.info = info
        this.sortLabels = sortLabels
        _states.value = emptyMap()
        val header =
            ChannelHeader(
                id = info.mid.toString(),
                title = info.name,
                avatarUrl = info.avatarUrl,
                bannerUrl = info.bannerUrl,
                subscriberCount = info.followerCount.takeIf { it >= 0 },
                description = info.description,
            )
        val tabs =
            listOf(
                ChannelTabDescriptor(kind = ChannelTabKind.Videos, title = videosLabel, params = TAB_PARAMS),
                ChannelTabDescriptor(kind = ChannelTabKind.Playlists, title = playlistsLabel, params = TAB_PARAMS),
            )
        return header to tabs
    }

    override fun ensureLoaded(
        kind: ChannelTabKind,
        params: String?,
    ) {
        if (_states.value[kind]?.loaded == true) return
        val channel = info ?: return
        val items =
            when (kind) {
                ChannelTabKind.Videos -> {
                    videos(channel, BilibiliChannelSort.LATEST)
                }

                ChannelTabKind.Playlists -> {
                    Pager(
                        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                        pagingSourceFactory = { BilibiliChannelPlaylistsPagingSource(api, channel.mid) },
                    ).flow.map { paging -> paging.map { playlist -> FeedItem.PlaylistItem(playlist) as FeedItem } }
                }

                else -> {
                    return
                }
            }
        val sortFilters = if (kind == ChannelTabKind.Videos) sortFilterGroups() else emptyList()
        _states.update {
            it +
                (
                    kind to
                        ChannelTabState(
                            items = items.cachedIn(scope),
                            filters = sortFilters,
                            selected = sortFilters.map { group -> group.selectedIndex },
                            isLoading = false,
                            loaded = true,
                        )
                )
        }
    }

    /** The Videos tab's sort chips: [BilibiliChannelSort] in declaration order, latest chosen first. */
    fun selectFilter(
        kind: ChannelTabKind,
        tabParams: String?,
        groupIndex: Int,
        optionIndex: Int,
    ) {
        val channel = info ?: return
        val state = _states.value[kind]
        if (kind != ChannelTabKind.Videos || groupIndex != 0 || state == null) return
        val sort = BilibiliChannelSort.entries.getOrNull(optionIndex) ?: return
        if (state.selected.firstOrNull() == optionIndex) return
        _states.update { states ->
            states + (kind to state.copy(items = videos(channel, sort).cachedIn(scope), selected = listOf(optionIndex)))
        }
    }

    private fun videos(
        channel: BilibiliChannelInfo,
        sort: BilibiliChannelSort,
    ): Flow<PagingData<FeedItem>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                BilibiliChannelVideosPagingSource(api, channel.mid, channel.name, channel.avatarUrl, sort)
            },
        ).flow.map { paging -> paging.map { video -> FeedItem.VideoItem(video) as FeedItem } }

    private fun sortFilterGroups(): List<ChannelFilterGroup> =
        listOf(
            ChannelFilterGroup(
                title = null,
                isDropdown = false,
                options =
                    BilibiliChannelSort.entries.mapIndexed { index, sort ->
                        ChannelFilterOption(label = sortLabels[sort].orEmpty(), selected = index == 0)
                    },
            ),
        )

    private companion object {
        const val PAGE_SIZE = 20
        const val TAB_PARAMS = "extractor"
    }
}
