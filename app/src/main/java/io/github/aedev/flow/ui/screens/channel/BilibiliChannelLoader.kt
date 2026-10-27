package io.github.aedev.flow.ui.screens.channel

import android.content.Context
import android.util.Log
import io.github.aedev.flow.R
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliChannelSort
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Uploader channel page for [ChannelViewModel]: header/tabs from native client as [ChannelTabState]. */
internal class BilibiliChannelLoader(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val uiState: MutableStateFlow<ChannelUiState>,
    private val community: ChannelCommunityController,
    private val onLoaded: (channelId: String, firstTab: ChannelTabKind?) -> Unit,
) {
    private val native = BilibiliNativeChannelController(scope) { bilibiliApi(appContext) }

    /** Bilibili tab source while the open channel is Bilibili; else [youTube]. */
    fun tabSource(youTube: ChannelTabSource): ChannelTabSource = if (uiState.value.serviceId == BILIBILI_SERVICE_ID) native else youTube

    /** Sort chip tapped: Bilibili's Videos tab sorts itself, any other channel goes to [youTube]. */
    fun selectFilter(
        youTube: ChannelTabController,
        kind: ChannelTabKind,
        tabParams: String?,
        groupIndex: Int,
        optionIndex: Int,
    ) = if (uiState.value.serviceId == BILIBILI_SERVICE_ID) {
        native.selectFilter(kind, tabParams, groupIndex, optionIndex)
    } else {
        youTube.selectFilter(kind, tabParams, groupIndex, optionIndex)
    }

    /** [youTube] tab states, or Bilibili's while the open channel is Bilibili. */
    fun tabStates(youTube: Flow<Map<ChannelTabKind, ChannelTabState>>): Flow<Map<ChannelTabKind, ChannelTabState>> =
        combine(uiState, youTube, native.states) { state, youTubeStates, bilibiliStates ->
            if (state.serviceId == BILIBILI_SERVICE_ID) bilibiliStates else youTubeStates
        }

    /**
     * Loads [channelUrl] if a Bilibili uploader; true when handled. Runs before service lookup: never falls through to YouTube.
     */
    fun load(channelUrl: String): Boolean {
        val mid = BilibiliRef.Uploader.ofLink(channelUrl)?.mid ?: return false
        scope.launch(PerformanceDispatcher.networkIO) { loadChannel(mid) }
        return true
    }

    private suspend fun loadChannel(mid: Long) {
        uiState.update { it.copy(isLoading = true, error = null) }
        val info =
            try {
                withTimeoutOrNull(LOAD_TIMEOUT_MS) { bilibiliApi(appContext).channelInfo(mid) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load Bilibili channel $mid: ${e.message}")
                null
            }
        if (info == null) {
            uiState.update {
                it.copy(error = appContext.getString(R.string.error_failed_to_load_channel), isLoading = false)
            }
            return
        }
        val (header, tabs) =
            native.reset(
                info,
                appContext.getString(R.string.tab_videos),
                appContext.getString(R.string.tab_playlists),
                mapOf(
                    BilibiliChannelSort.LATEST to appContext.getString(R.string.filter_newest),
                    BilibiliChannelSort.POPULAR to appContext.getString(R.string.filter_popular),
                ),
            )
        val channelId = info.mid.toString()
        uiState.update {
            it.copy(
                channelId = channelId,
                serviceId = BILIBILI_SERVICE_ID,
                header = header,
                tabs = tabs,
                selectedTab = tabs.firstOrNull()?.kind,
                isLoading = false,
            )
        }
        community.reset(channelId, info.name, header.avatarUrl, BILIBILI_SERVICE_ID)
        onLoaded(channelId, tabs.firstOrNull()?.kind)
    }

    private companion object {
        const val TAG = "BilibiliChannelLoader"
        const val LOAD_TIMEOUT_MS = 20_000L
    }
}
