package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.HomeFeedCacheFilters
import io.github.aedev.flow.data.recommendation.FeedExclusions
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.ui.screens.home.FeedTasteProfile
import io.github.aedev.flow.ui.screens.home.GraphCandidate
import io.github.aedev.flow.ui.screens.home.assembleHomeFeed
import io.github.aedev.flow.ui.screens.home.buildHomeFeedLanes
import io.github.aedev.flow.ui.screens.home.dynamicFreshSubSlots
import io.github.aedev.flow.ui.screens.home.feedTasteProfile
import io.github.aedev.flow.ui.screens.home.storedSubscriptionVideos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import io.github.aedev.flow.data.model.Video as FlowVideo

// 5-min cache of assembled home feed, keyed by serviceId.
private const val HOME_FEED_CACHE_TTL_MS = 5 * 60 * 1000L

private data class HomeFeedCacheEntry(
    val items: List<InfoItem>,
    val timestampMs: Long,
)

private val homeFeedCache = ConcurrentHashMap<Int, HomeFeedCacheEntry>()

/** Concurrent FlowNeuro discovery round; [resetDepth] fresh session vs load-more. Query count = native wave-1. */
private suspend fun CoroutineScope.fetchDiscoveryVideos(
    repo: YouTubeRepository,
    resetDepth: Boolean,
): List<FlowVideo> =
    runCatching {
        val queries = FlowNeuroEngine.generateDiscoveryQueries(resetDepth = resetDepth)
        queries
            .take(3)
            .map { query ->
                async { runCatching { repo.searchVideos(query).first }.getOrDefault(emptyList()) }
            }.awaitAll()
            .flatten()
    }.getOrDefault(emptyList())

/** Home-feed builder inputs: watched, exclusions, brain, taste, subscribed avatars. */
private data class HomeFeedContext(
    val watched: Set<String>,
    val exclusions: FeedExclusions,
    val brain: UserBrain,
    val taste: FeedTasteProfile,
    val subAvatarMap: Map<String, String>,
)

private suspend fun HistoryDbHelper.buildHomeFeedContext(): HomeFeedContext {
    val hideWatched = playerPreferences().hideWatchedVideosFromHome.first()
    val watched = if (hideWatched) viewHistory().getAllWatchedVideoIds() else emptySet()
    val exclusions = runCatching { FlowNeuroEngine.feedExclusions() }.getOrDefault(FeedExclusions.NONE)
    val brain = runCatching { FlowNeuroEngine.getBrainSnapshot() }.getOrElse { UserBrain() }
    val taste = feedTasteProfile(brain, FlowNeuroEngine.getPersona(brain))
    val subAvatarMap =
        runCatching {
            subscriptionRepository()
                .getAllSubscriptions()
                .first()
                .filter { it.channelThumbnail.isNotEmpty() }
                .associate { it.channelId to it.channelThumbnail }
        }.getOrDefault(emptyMap())
    return HomeFeedContext(watched, exclusions, brain, taste, subAvatarMap)
}

/** Dedup by id, rank via FlowNeuroEngine (unranked on failure), convert. For [continueDiscoveryFeed]. */
private suspend fun HistoryDbHelper.rankAndConvert(
    videos: List<FlowVideo>,
    serviceId: Int,
): List<StreamInfoItem> {
    val deduped = LinkedHashMap<String, FlowVideo>()
    // This page's serviceId only: watch/channel links built for one service.
    for (v in videos) if (v.id.isNotBlank() && v.serviceId == serviceId) deduped.putIfAbsent(v.id, v)
    if (deduped.isEmpty()) return emptyList()

    val subIds = subscriptionRepository().getAllSubscriptionIds()
    val ranked =
        runCatching { FlowNeuroEngine.rank(deduped.values.toList(), subIds) }
            .getOrDefault(deduped.values.toList())
    return ranked.map { it.toStreamInfoItem(serviceId) }
}

/**
 * Home feed via native wave-1 pipeline ([buildHomeFeedLanes]/[assembleHomeFeed]); YouTube-only, no wave-2 / persistent cache. Upstream-
 * owned: re-verify after sync. Second = more available (no Page token).
 */
fun HistoryDbHelper.buildAndRankHomeFeed(serviceId: Int): Pair<List<InfoItem>, Boolean> =
    runBlocking {
        val cacheKey = serviceId
        val cached = homeFeedCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.timestampMs < HOME_FEED_CACHE_TTL_MS) {
            return@runBlocking cached.items to cached.items.isNotEmpty()
        }

        ensureFlowNeuroInitialized()
        val repo = youTubeRepository()
        val sources = homeFeedSources()
        val subIds = subscriptionRepository().getAllSubscriptionIds()
        val context = buildHomeFeedContext()
        val cacheFilters: suspend () -> HomeFeedCacheFilters = {
            HomeFeedCacheFilters(watchedVideoIds = context.watched, exclusions = context.exclusions)
        }

        lateinit var rawDiscovery: List<FlowVideo>
        lateinit var rawViral: List<FlowVideo>
        lateinit var rawRelated: List<GraphCandidate>
        lateinit var rssFeed: List<FlowVideo>
        supervisorScope {
            val discoveryDeferred = async { fetchDiscoveryVideos(repo, resetDepth = true) }
            val viralDeferred = async { runCatching { trendingVideos() }.getOrDefault(emptyList()) }
            val relatedDeferred =
                async {
                    runCatching {
                        val seedInputs = sources.historySeedInputs()
                        val seedIds = FlowNeuroEngine.selectRelatedSeeds(seedInputs)
                        sources.fetchRelatedGraph(seedInputs, seedIds, cacheFilters).candidates
                    }.getOrDefault(emptyList())
                }
            val rssDeferred = async { storedSubscriptionFeed() }

            rawDiscovery = discoveryDeferred.await()
            rawViral = viralDeferred.await()
            rawRelated = relatedDeferred.await()
            rssFeed = rssDeferred.await()
        }

        // No separate trending lane: trending joins discovery.
        val rawSubs = if (subIds.isNotEmpty()) rssFeed.storedSubscriptionVideos(now) else emptyList()
        val lanes =
            buildHomeFeedLanes(
                rawSubs = rawSubs,
                rawDiscovery = rawDiscovery + rawViral,
                rawMemory = emptyList(),
                rawRelated = rawRelated,
                rssFeed = rssFeed,
                watched = context.watched,
                exclusions = context.exclusions,
                isRecentlyShown = { id -> NeuroScoring.isRecentlySeen(context.brain.feedHistory[id], now) },
                taste = context.taste,
                now = now,
                freshSlotTarget = dynamicFreshSubSlots(subIds.size),
                subAvatarMap = context.subAvatarMap,
                rank = { pool -> runCatching { FlowNeuroEngine.rank(pool, subIds) }.getOrDefault(pool) },
            )
        val mix =
            assembleHomeFeed(
                lanes = lanes,
                onScreenIds = emptySet(),
                subCount = subIds.size,
                totalInteractions = context.brain.totalInteractions,
            )

        // Subscriptions/history span services; filter to this page's serviceId first (ServiceIdHelpers.kt).
        val result = mix.videos.filter { it.serviceId == serviceId }.map { it.toStreamInfoItem(serviceId) }
        if (result.isEmpty()) return@runBlocking emptyList<InfoItem>() to false

        homeFeedCache[cacheKey] = HomeFeedCacheEntry(result, now)
        result to true
    }

/** Load more: deeper discovery round, ranked, uncached; discovery depth shared with native. */
fun HistoryDbHelper.continueDiscoveryFeed(serviceId: Int): Pair<List<InfoItem>, Boolean> =
    runBlocking {
        ensureFlowNeuroInitialized()
        val repo = youTubeRepository()

        val videos = fetchDiscoveryVideos(repo, resetDepth = false)
        val result = rankAndConvert(videos, serviceId)
        result to result.isNotEmpty()
    }

/** Trending kiosk, unranked, no pagination; ranked pool: [buildAndRankHomeFeed]. */
fun HistoryDbHelper.fetchTrendingItems(serviceId: Int): List<StreamInfoItem> =
    runBlocking {
        trendingVideos().map { it.toStreamInfoItem(serviceId) }
    }

/** YouTube Music trending video chart for the user's trending region. */
private suspend fun HistoryDbHelper.trendingVideos(): List<FlowVideo> {
    val region = playerPreferences().trendingRegion.first()
    return YouTube
        .musicVideoChart(region)
        .getOrNull()
        ?.entries
        .orEmpty()
}
