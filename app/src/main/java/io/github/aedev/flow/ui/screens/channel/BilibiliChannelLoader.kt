package io.github.aedev.flow.ui.screens.channel

import android.content.Context
import android.util.Log
import io.github.aedev.flow.R
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliChannelId
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

/**
 * Opens a Bilibili uploader's channel page for [ChannelViewModel], so that upstream file only carries a
 * call. Bilibili's header and tabs come from the native client, not InnerTube, but arrive as the same
 * [ChannelTabState], so the screen never needs to know which service a tab's data came from.
 *
 * It writes the page into the view model's own [uiState]; what only the view model can do once the
 * channel is in place (its subscription, note and first tab) goes through [onLoaded].
 */
internal class BilibiliChannelLoader(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val uiState: MutableStateFlow<ChannelUiState>,
    private val community: ChannelCommunityController,
    private val onLoaded: (channelId: String, firstTab: ChannelTabKind?) -> Unit,
) {
    private val native = BilibiliNativeChannelController(scope) { bilibiliApi(appContext) }

    /** The Bilibili tab source while the open channel is a Bilibili one, [youTube] otherwise. */
    fun tabSource(youTube: ChannelTabSource): ChannelTabSource = if (uiState.value.serviceId == BILIBILI_SERVICE_ID) native else youTube

    /** [youTube]'s tab states, or Bilibili's while the open channel is a Bilibili one. */
    fun tabStates(youTube: Flow<Map<ChannelTabKind, ChannelTabState>>): Flow<Map<ChannelTabKind, ChannelTabState>> =
        combine(uiState, youTube, native.states) { state, youTubeStates, bilibiliStates ->
            if (state.serviceId == BILIBILI_SERVICE_ID) bilibiliStates else youTubeStates
        }

    /**
     * Loads the channel when [channelUrl] is a Bilibili uploader, and returns true. Checked before any
     * service lookup so a Bilibili uploader can never fall through to YouTube.
     */
    fun load(channelUrl: String): Boolean {
        val mid = BilibiliChannelId.midOf(channelUrl) ?: return false
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
