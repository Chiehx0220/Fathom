package io.github.aedev.flow.ui.screens.settings.hidden

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.feed.BlockedContentStore
import io.github.aedev.flow.data.recommendation.BlockedChannelNames
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class NamedItem(
    val id: String,
    val name: String,
)

@Immutable
data class HiddenContent(
    /** Blank [NamedItem.name]: not resolved yet. */
    val channels: List<NamedItem> = emptyList(),
    val artists: List<NamedItem> = emptyList(),
    /** Unresolved [channels] names are being fetched. */
    val resolvingNames: Boolean = false,
    val loading: Boolean = true,
) {
    val count: Int get() = channels.size + artists.size
}

/** Blocked channels and artists; names unknown locally are fetched once. */
@HiltViewModel
internal class HiddenContentViewModel
    @Inject
    constructor(
        private val blockedContent: BlockedContentStore,
        private val blockedNames: BlockedChannelNames,
        private val musicBrain: MusicBrainEngine,
    ) : SettingsViewModel() {
        private val _state = MutableStateFlow(HiddenContent())
        val state: StateFlow<HiddenContent> = _state

        private var resolveJob: Job? = null

        init {
            viewModelScope.launch {
                combine(blockedContent.blockedChannelIds, musicBrain.hiddenArtists) { channels, _ -> channels }.collect { channels ->
                    val known = blockedNames.known(channels)
                    val artists = runCatching { musicBrain.getBlockedArtistsWithNames() }.getOrDefault(emptyList())
                    _state.update {
                        it.copy(
                            channels = hiddenChannelOrder(channels.map { id -> NamedItem(id, known[id].orEmpty()) }),
                            artists = artists.map { (id, name) -> NamedItem(id, name) },
                            loading = false,
                        )
                    }
                }
            }
        }

        /** Fills names in place and sorts once at the end, so rows keep their position while loading. */
        fun resolveMissingNames() {
            if (resolveJob?.isActive == true) return
            resolveJob =
                viewModelScope.launch {
                    val loaded = _state.first { !it.loading }
                    val missing = loaded.channels.filter { it.name.isBlank() }.map { it.id }
                    if (missing.isEmpty()) return@launch
                    _state.update { it.copy(resolvingNames = true) }
                    try {
                        blockedNames.resolve(missing, blockedContent.blockedChannelIds.first()) { names ->
                            _state.update { state ->
                                state.copy(channels = state.channels.map { item -> names[item.id]?.let { item.copy(name = it) } ?: item })
                            }
                        }
                    } finally {
                        _state.update { it.copy(channels = hiddenChannelOrder(it.channels), resolvingNames = false) }
                    }
                }
        }

        fun unblockChannel(channelId: String) {
            write {
                blockedContent.unblockChannel(channelId)
                blockedNames.forget(channelId)
            }
        }

        fun unblockArtist(artistKey: String) {
            write { musicBrain.unblockArtist(artistKey) }
        }
    }

/** Named channels by name, then unnamed by id. */
internal fun hiddenChannelOrder(channels: List<NamedItem>): List<NamedItem> {
    val (named, unnamed) = channels.partition { it.name.isNotBlank() }
    return named.sortedBy { it.name.lowercase() } + unnamed.sortedBy { it.id }
}
