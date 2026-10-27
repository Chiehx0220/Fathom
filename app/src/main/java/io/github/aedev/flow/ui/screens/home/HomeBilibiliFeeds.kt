package io.github.aedev.flow.ui.screens.home

import android.content.Context
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.serviceIdOfChannel
import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.homeContentSourceFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.subscriptions.latestVideos
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.player.stream.BilibiliVideoMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

// Home feed Bilibili lane, outside HomeViewModel.kt: upstream YouTube-side changes merge cleanly.

/**
 * Wave-1 candidate fetch: skipped when nothing to fetch, time-bounded, failure → no candidates.
 */
internal suspend fun <T> fetchSafely(
    enabled: Boolean = true,
    timeoutMs: Long = 8_000L,
    fetch: suspend () -> List<T>,
): List<T> {
    if (!enabled) return emptyList()
    return withTimeoutOrNull(timeoutMs) {
        runCatching { fetch() }.getOrElse { emptyList() }
    }.orEmpty()
}

private const val BILIBILI_VIDEOS_PER_CHANNEL = 5
private const val BILIBILI_SUBS_TOTAL_LIMIT = 60
private const val BILIBILI_LIVES_LIMIT = 6

internal data class BilibiliWave1Feeds(
    val subs: List<Video>,
    /** Trending + live videos not found by search. */
    val viral: List<Video>,
)

internal fun Video.isBilibili(): Boolean = serviceIdOfVideo(id, serviceId) == BILIBILI_SERVICE_ID

/** [pageOrigins] without search label on Bilibili trending/live cards. */
internal fun pageOriginsWithBilibili(
    videos: List<Video>,
    sourceOf: (String) -> FeedSource?,
    seedTitles: Map<String, String>,
    queries: Map<String, String>,
    followed: Set<String>,
    neighbourPointers: Map<String, String> = emptyMap(),
): Map<String, HomeCardOrigin> {
    val origins = pageOrigins(videos, sourceOf, seedTitles, queries, followed, neighbourPointers)
    val unsearched =
        videos
            .filter { it.isBilibili() && it.id !in queries && origins[it.id]?.kind == HomeCardOrigin.Kind.SEARCH }
            .mapTo(HashSet()) { it.id }
    return origins - unsearched
}

/** Late-arriving cards: same Home source filter as the feed. */
internal suspend fun List<Video>.allowedBy(preferences: PlayerPreferences): List<Video> {
    val filter = preferences.homeContentSourceFilter.first()
    return filter { filter.allows(it) }
}

/** Persisted [HomeContentSourceFilter] (not in upstream Home state); initial YouTube. */
internal fun PlayerPreferences.homeContentSourceFilterIn(
    scope: CoroutineScope,
    started: SharingStarted,
): StateFlow<HomeContentSourceFilter> = homeContentSourceFilter.stateIn(scope, started, HomeContentSourceFilter.YOUTUBE)

/**
 * Home mix under source filter (YouTube/Bilibili/mix), applied after assembly (only fresh-subs lane holds non-YouTube); Subscriptions,
 * Search untouched.
 */
internal fun List<Video>.shapedBy(filter: HomeContentSourceFilter): List<Video> =
    filter.serviceId?.let { id -> filter { it.serviceId == id } } ?: spreadByService()

/** [shapedBy] for a backlog that is only narrowed, never reordered. */
internal fun List<Video>.onlyFrom(filter: HomeContentSourceFilter): List<Video> =
    filter.serviceId?.let { id -> filter { it.serviceId == id } } ?: this

/** Home page of cards: [filter]-allowed services only, spread evenly. */
internal fun List<Video>.forSourceFilter(filter: HomeContentSourceFilter): List<Video> = filter { filter.allows(it) }.spreadByService()

// Bilibili wave-1 fetches (subs, trending + live), out of loadFlowFeed(): smaller merge surface.
internal fun CoroutineScope.launchBilibiliWave1Feeds(
    subscriptionRepository: SubscriptionRepository,
    context: Context,
    filter: HomeContentSourceFilter,
): Deferred<BilibiliWave1Feeds> =
    async {
        if (!filter.allows(BILIBILI_SERVICE_ID)) return@async BilibiliWave1Feeds(emptyList(), emptyList())
        val bilibili = bilibiliApi(context)
        // getAllSubscriptionIds() has no per-service info: filter to Bilibili channels (right extractor).
        val bilibiliSubs =
            runCatching {
                subscriptionRepository
                    .getAllSubscriptions()
                    .first()
                    .filter { serviceIdOfChannel(it.channelId, it.serviceId) == BILIBILI_SERVICE_ID }
            }.getOrElse { emptyList() }

        // getSubscriptionFeed() rotation cursor is YouTube-tuned: Bilibili gets own small uncursored fetch.
        val deferredSubs =
            async {
                fetchSafely(enabled = bilibiliSubs.isNotEmpty()) {
                    bilibiliSubs
                        .mapNotNull { sub -> sub.channelId.toLongOrNull()?.let { mid -> sub to mid } }
                        .map { (sub, mid) ->
                            async {
                                runCatching {
                                    bilibili.latestVideos(mid, BILIBILI_VIDEOS_PER_CHANNEL, sub.channelName, sub.channelThumbnail)
                                }.getOrDefault(emptyList())
                            }
                        }.awaitAll()
                        .flatten()
                        .sortedByDescending { it.timestamp }
                        .take(BILIBILI_SUBS_TOTAL_LIMIT)
                }
            }

        // Popular videos + a few live rooms.
        val deferredViral =
            async {
                val popular = async { fetchSafely { bilibili.popular().map(BilibiliVideoMapper::videoFromRelated) } }
                val lives =
                    async {
                        fetchSafely {
                            bilibili.recommendedLives().take(BILIBILI_LIVES_LIMIT).map(BilibiliVideoMapper::videoFromLiveItem)
                        }
                    }
                popular.await() + lives.await()
            }

        BilibiliWave1Feeds(
            subs = deferredSubs.await(),
            viral = deferredViral.await(),
        )
    }

/** Interleaves services evenly, order within each kept (a ranker would otherwise put one service first). */
internal fun List<Video>.spreadByService(): List<Video> {
    val byService = groupBy { it.serviceId }
    if (byService.size < 2) return this
    return byService.values
        .flatMap { group -> group.mapIndexed { index, video -> video to (index + 0.5) / group.size } }
        .sortedBy { it.second }
        .map { it.first }
}
