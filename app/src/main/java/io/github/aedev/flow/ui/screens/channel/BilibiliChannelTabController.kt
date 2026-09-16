package io.github.aedev.flow.ui.screens.channel

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import io.github.aedev.flow.data.paging.ChannelPlaylistsPagingSource
import io.github.aedev.flow.data.paging.ChannelVideosPagingSource
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
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

/**
 * Bilibili (and any other non-YouTube service) has no notion of Flow's own InnerTube tab
 * controller ([ChannelTabController]) - it comes back from the generic NewPipe/PipePipeExtractor
 * [ChannelInfo] instead, so its Videos/Playlists tabs are paged and published in the same
 * [ChannelTabState] shape here, kept separate so the screen never needs to know which source a
 * tab's data came from.
 */
internal class BilibiliChannelTabController(
    private val scope: CoroutineScope,
) {
    private val _states = MutableStateFlow<Map<ChannelTabKind, ChannelTabState>>(emptyMap())
    val states: StateFlow<Map<ChannelTabKind, ChannelTabState>> = _states.asStateFlow()

    private var channelInfo: ChannelInfo? = null
    private var videosTab: ListLinkHandler? = null
    private var playlistsTab: ListLinkHandler? = null

    /**
     * Scans [channelInfo]'s tabs for a Videos/Playlists match by name/url and resets loaded state,
     * returning the header + tab descriptors to publish to [ChannelUiState].
     */
    fun reset(
        channelInfo: ChannelInfo,
        videosLabel: String,
        playlistsLabel: String,
    ): Pair<ChannelHeader, List<ChannelTabDescriptor>> {
        this.channelInfo = channelInfo
        videosTab = null
        playlistsTab = null
        _states.value = emptyMap()

        for (tab in channelInfo.tabs) {
            try {
                val tabName = tab.contentFilters.joinToString { it.name }
                val tabUrl = tab.url.orEmpty()
                val isPlaylists =
                    tabName.contains("playlist", ignoreCase = true) || tabUrl.contains("/playlists", ignoreCase = true)
                val isVideos =
                    !isPlaylists &&
                        (tabName.contains("video", ignoreCase = true) || tabUrl.contains("/videos", ignoreCase = true))
                if (isVideos) videosTab = tab
                if (isPlaylists) playlistsTab = tab
            } catch (e: Exception) {
                Log.e(TAG, "Error checking non-YouTube tab", e)
            }
        }

        val avatarUrl =
            channelInfo.avatars.maxByOrNull { it.height }?.url
                ?: channelInfo.avatars.firstOrNull()?.url
                ?: ""
        val header =
            ChannelHeader(
                id = channelInfo.id,
                title = channelInfo.name,
                avatarUrl = avatarUrl,
                bannerUrl = channelInfo.banners.maxByOrNull { it.height }?.url,
                subscriberCount = channelInfo.subscriberCount.takeIf { it >= 0 },
                description = channelInfo.description,
            )
        val tabs =
            buildList {
                if (videosTab != null) {
                    add(ChannelTabDescriptor(kind = ChannelTabKind.Videos, title = videosLabel, params = TAB_PARAMS))
                }
                if (playlistsTab != null) {
                    add(ChannelTabDescriptor(kind = ChannelTabKind.Playlists, title = playlistsLabel, params = TAB_PARAMS))
                }
            }
        return header to tabs
    }

    fun ensureLoaded(kind: ChannelTabKind) {
        if (_states.value[kind]?.loaded == true) return
        val info = channelInfo ?: return

        when (kind) {
            ChannelTabKind.Videos -> {
                val tab = videosTab ?: return
                val pager =
                    Pager(
                        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                        pagingSourceFactory = { ChannelVideosPagingSource(tab, info) },
                    ).flow
                        .map { paging -> paging.map { video -> FeedItem.VideoItem(video) as FeedItem } }
                        .cachedIn(scope)
                _states.update { it + (kind to ChannelTabState(items = pager, isLoading = false, loaded = true)) }
            }

            ChannelTabKind.Playlists -> {
                val tab = playlistsTab ?: return
                val pager =
                    Pager(
                        config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
                        pagingSourceFactory = { ChannelPlaylistsPagingSource(tab, info.serviceId) },
                    ).flow
                        .map { paging -> paging.map { playlist -> FeedItem.PlaylistItem(playlist) as FeedItem } }
                        .cachedIn(scope)
                _states.update { it + (kind to ChannelTabState(items = pager, isLoading = false, loaded = true)) }
            }

            else -> Unit
        }
    }

    private companion object {
        const val TAG = "BilibiliChannelTabController"
        const val PAGE_SIZE = 20

        /** Placeholder tab params for a non-YouTube channel - real params only mean something to
         *  YouTube's InnerTube tab controller, which these tabs never go through. */
        const val TAB_PARAMS = "extractor"
    }
}
