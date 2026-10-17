package io.github.aedev.flow.ui.screens.home

import android.content.Context
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.serviceIdOfChannel
import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.subscriptions.latestVideos
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.player.stream.BilibiliVideoMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// The home feed's Bilibili lane, kept out of HomeViewModel.kt so upstream changes to that file's
// YouTube side merge without touching it.

/**
 * Every wave-1 candidate fetch wants the same shape - skip the call outright when there's nothing
 * to fetch for, bound it so one slow service can't stall the whole wave, and treat any failure as
 * "no candidates" rather than propagating - so it's written once here instead of at each call site.
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
    /** Trending and live videos, which no search found. */
    val viral: List<Video>,
)

internal fun Video.isBilibili(): Boolean = serviceIdOfVideo(id, serviceId) == BILIBILI_SERVICE_ID

/** [pageOrigins], without a search label on Bilibili trending and live cards: no search found them. */
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

/** Cards that arrive after the feed loaded face the same Home source filter as the feed they join. */
internal suspend fun List<Video>.allowedBy(preferences: PlayerPreferences): List<Video> {
    val filter = preferences.homeContentSourceFilter.first()
    return filter { filter.allows(it) }
}

/** A page of cards for Home: only the services [filter] allows, spread evenly through the page. */
internal fun List<Video>.forSourceFilter(filter: HomeContentSourceFilter): List<Video> = filter { filter.allows(it) }.spreadByService()

// Bilibili's two wave-1 fetches (subs, then trending and live), factored out of loadFlowFeed()'s
// supervisorScope to reduce merge-conflict surface with upstream's YouTube-side fetch logic there.
internal fun CoroutineScope.launchBilibiliWave1Feeds(
    subscriptionRepository: SubscriptionRepository,
    context: Context,
    filter: HomeContentSourceFilter,
): Deferred<BilibiliWave1Feeds> =
    async {
        if (!filter.allows(BILIBILI_SERVICE_ID)) return@async BilibiliWave1Feeds(emptyList(), emptyList())
        val bilibili = bilibiliApi(context)
        // getAllSubscriptionIds() has no per-service info - filter to Bilibili channels here so
        // the fetch below routes through the right extractor.
        val bilibiliSubs =
            runCatching {
                subscriptionRepository
                    .getAllSubscriptions()
                    .first()
                    .filter { serviceIdOfChannel(it.channelId, it.serviceId) == BILIBILI_SERVICE_ID }
            }.getOrElse { emptyList() }

        // getSubscriptionFeed()'s rotation cursor is YouTube-tuned - Bilibili gets its own small,
        // uncursored fetch instead.
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

        // Popular videos, plus a few of the rooms that are live right now.
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

/**
 * Spreads each service's videos evenly through the list, keeping their relative order. A ranker that
 * scores one service higher would otherwise put all of it first and the other service last.
 */
internal fun List<Video>.spreadByService(): List<Video> {
    val byService = groupBy { it.serviceId }
    if (byService.size < 2) return this
    return byService.values
        .flatMap { group -> group.mapIndexed { index, video -> video to (index + 0.5) / group.size } }
        .sortedBy { it.second }
        .map { it.first }
}
