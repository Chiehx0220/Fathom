package io.github.aedev.flow.ui.screens.channel

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliChannelInfo
import io.github.aedev.flow.data.paging.BilibiliChannelPlaylistsPagingSource
import io.github.aedev.flow.data.paging.BilibiliChannelVideosPagingSource
import io.github.aedev.flow.innertube.pages.channel.ChannelHeader
import io.github.aedev.flow.innertube.pages.channel.ChannelTabDescriptor
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * A Bilibili channel's header and tabs through the native client: both tabs read [BilibiliApi].
 */
internal class BilibiliNativeChannelController(
    private val scope: CoroutineScope,
    apiProvider: () -> BilibiliApi,
) : ChannelTabSource {
    private val api by lazy(apiProvider)

    private val _states = MutableStateFlow<Map<ChannelTabKind, ChannelTabState>>(emptyMap())
    override val states: StateFlow<Map<ChannelTabKind, ChannelTabState>> = _states.asStateFlow()

    private var info: BilibiliChannelInfo? = null

    fun reset(
        info: BilibiliChannelInfo,
        videosLabel: String,
        playlistsLabel: String,
    ): Pair<ChannelHeader, List<ChannelTabDescriptor>> {
        this.info = info
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
                    Pager(
                        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                        pagingSourceFactory = {
                            BilibiliChannelVideosPagingSource(api, channel.mid, channel.name, channel.avatarUrl)
                        },
                    ).flow.map { paging -> paging.map { video -> FeedItem.VideoItem(video) as FeedItem } }
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
        _states.update { it + (kind to ChannelTabState(items = items.cachedIn(scope), isLoading = false, loaded = true)) }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val TAB_PARAMS = "extractor"
    }
}
