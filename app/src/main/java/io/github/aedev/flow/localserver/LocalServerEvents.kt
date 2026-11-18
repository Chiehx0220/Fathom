package io.github.aedev.flow.localserver

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.likedVideosDataStore
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

internal const val EVENTS_PATH = "/events"

/** WebSocket paths; same-origin only. */
internal val SOCKET_PATHS = setOf(REMOTE_PATH, EVENTS_PATH)

/** Frame = topic name only. */
internal const val TOPIC_SETTINGS = "settings"
internal const val TOPIC_SUBSCRIPTIONS = "subscriptions"
internal const val TOPIC_LIKES = "likes"
internal const val TOPIC_WATCH_LATER = "later"
internal const val TOPIC_PROFILES = "profiles"

private const val SETTLE_MS = 150L

/** Topic per source change; initial value skipped; bursts debounced. */
@OptIn(FlowPreview::class)
internal fun changeTopics(
    sources: List<Pair<String, Flow<Any?>>>,
    settleMs: Long = SETTLE_MS,
): Flow<String> =
    sources
        .map { (topic, values) ->
            values
                .distinctUntilChanged()
                .drop(1)
                .debounce(settleMs)
                .map { topic }
        }.merge()

/** Settings the page reads; any entry change = topic change. */
private fun settingsValues(context: Context): Flow<List<Any?>> {
    val preferences = PlayerPreferences(context)
    val data = LocalDataManager(context)
    val flows: List<Flow<Any?>> =
        listOf(
            preferences.hideWatchedVideosFromHome,
            preferences.shortsContentEnabled,
            preferences.deepFlowActive,
            preferences.deepFlowActivatedAt,
            preferences.deepFlowExpireHours,
            preferences.sponsorBlockEnabled,
            preferences.showProfileInTopBar,
            data.breakReminder,
            data.breakFrequency,
        )
    return combine(flows) { it.toList() }
}

internal fun appChangeTopics(dbHelper: HistoryDbHelper): Flow<String> {
    val context = dbHelper.appContext
    val profiles = EntryPointAccessors.fromApplication(context, LocalServerProfilesEntryPoint::class.java)
    return changeTopics(
        listOf(
            TOPIC_SETTINGS to settingsValues(context),
            TOPIC_SUBSCRIPTIONS to SubscriptionRepository.getInstance(context).getAllSubscriptions(),
            TOPIC_LIKES to context.likedVideosDataStore.data.map { it.asMap() },
            TOPIC_WATCH_LATER to dbHelper.playlistRepository().getWatchLaterIdsFlow(),
            TOPIC_PROFILES to profiles.profileRepository().state,
        ),
    )
}

/** Sends topics for the connection's lifetime. */
internal suspend fun serveEvents(
    session: DefaultWebSocketSession,
    topics: Flow<String>,
) = coroutineScope {
    val sender = launch { topics.collect { session.outgoing.send(Frame.Text(it)) } }
    try {
        session.incoming.consumeEach { }
    } finally {
        sender.cancel()
    }
}
