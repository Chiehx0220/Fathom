package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.HomeFeedCacheFilters
import io.github.aedev.flow.data.model.isYouTubeServiceId
import io.github.aedev.flow.data.recommendation.FeedExclusions
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.player.PlayerRelatedVideosPolicy
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
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import io.github.aedev.flow.data.model.Video as FlowVideo

// 5-min cache of assembled home feed, keyed by serviceId.
private const val HOME_FEED_CACHE_TTL_MS = 5 * 60 * 1000L

private data class HomeFeedCacheEntry(
    val items: List<InfoItem>,
    val timestampMs: Long,
)

private val homeFeedCache = java.util.concurrent.ConcurrentHashMap<Int, HomeFeedCacheEntry>()

/** One round of FlowNeuro discovery queries, concurrent. `resetDepth`: fresh session vs load-more. Query count = native wave-1.
 * Shared by [buildAndRankHomeFeed], [continueDiscoveryFeed]. */
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

/** Home-feed builder inputs: watched gating (`hideWatchedVideosFromHome`), FlowNeuroEngine block/suppress lists,
 * brain snapshot + taste profile, subscribed-channel avatars. */
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

/** Dedup by video id, rank via FlowNeuroEngine (unranked on failure), convert to [StreamInfoItem]. Used by [continueDiscoveryFeed]. */
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
 * Assembles/ranks home feed through native wave-1 pipeline ([buildHomeFeedLanes]/[assembleHomeFeed], `ui.screens.home`), not a port:
 * quota-balanced source blending, same-channel spacing, duration/format-fit demotion, fresh-upload pinning, related (/next graph) lane.
 *
 * UPSTREAM COUPLING: [buildHomeFeedLanes], [assembleHomeFeed], [feedTasteProfile], [dynamicFreshSubSlots] are upstream-owned
 * (`HomeFeedAssembly.kt`/`HomeFeedRanking.kt`, PR #1040); changes there are not merge conflicts here: re-verify compile + home feed after every sync.
 *
 * `rssFeed`/`homeFeedSources()`/`youTubeRepository()`: same Hilt singletons as native Home ([localServerEntryPoint]/[YouTubeRepository.getInstance]).
 *
 * Divergence from native:
 * - YouTube-only; no Bilibili lane.
 * - No wave-2 enrichment / persistent feed cache / reserve page (long-lived Compose screen concerns).
 *
 * Second return value: [continueDiscoveryFeed] has more. No NewPipeExtractor Page: `YoutubeTrendingExtractor` has no continuation; "load more" = deeper discovery-query round.
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

/** Load more: deeper discovery-query round, ranked as [buildAndRankHomeFeed]. Uncached, no subscription-feed re-pull (subs lane first-load only, as native).
 * Shares FlowNeuroEngine discovery-depth counter with native. */
fun HistoryDbHelper.continueDiscoveryFeed(serviceId: Int): Pair<List<InfoItem>, Boolean> =
    runBlocking {
        ensureFlowNeuroInitialized()
        val repo = youTubeRepository()

        val videos = fetchDiscoveryVideos(repo, resetDepth = false)
        val result = rankAndConvert(videos, serviceId)
        result to result.isNotEmpty()
    }

// Shorts feed not served here; native: ShortsRepository, ShortsDiscoveryEngine.

/** Trending kiosk, unranked: `handleApiHome()` raw JSON feed (vs `handleApiRecommendations()`: ranked pool of [buildAndRankHomeFeed]).
 * No pagination (`YoutubeTrendingExtractor` has no next page). */
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

/**
 * Reorders `items` via FlowNeuroEngine; order only, never composition. Original order on failure.
 */
fun HistoryDbHelper.rankWithFlowNeuro(
    items: List<InfoItem>,
    serviceId: Int,
): List<InfoItem> {
    val streamItems = items.filterIsInstance<StreamInfoItem>()
    if (streamItems.isEmpty()) return items
    return try {
        runBlocking {
            ensureFlowNeuroInitialized()
            val userSubs = subscriptionRepository().getAllSubscriptionIds()

            val videoById = LinkedHashMap<String, StreamInfoItem>()
            val videos = ArrayList<FlowVideo>(streamItems.size)
            for (item in streamItems) {
                val video = item.toFlowVideo(serviceId)
                if (video.id.isNotBlank() && !videoById.containsKey(video.id)) {
                    videoById[video.id] = item
                    videos.add(video)
                }
            }
            if (videos.isEmpty()) return@runBlocking items

            val ranked = FlowNeuroEngine.rank(videos, userSubs)
            val rankedItems: List<InfoItem> = ranked.mapNotNull { videoById[it.id] }

            val rankedSet = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<InfoItem, Boolean>())
            rankedSet.addAll(rankedItems)
            val leftovers = items.filter { it !in rankedSet }

            rankedItems + leftovers
        }
    } catch (e: Exception) {
        serverLog("FlowNeuro ranking failed, falling back to original order: " + e.message)
        items
    }
}

/** Click/watch signal → FlowNeuroEngine. Best-effort. */
fun HistoryDbHelper.reportFlowNeuroInteraction(
    info: StreamInfo,
    serviceId: Int,
    type: InteractionType,
    percentWatched: Float = 0f,
) {
    try {
        runBlocking {
            ensureFlowNeuroInitialized()
            FlowNeuroEngine.onVideoInteraction(info.toFlowVideo(serviceId), type, percentWatched)
        }
    } catch (e: Exception) {
        serverLog("FlowNeuro interaction-signal error: " + e.message)
    }
}

/**
 * Related via `PlayerRelatedVideosPolicy`, app watch-page order: InnerTube `/next` first, extractor list (no network) only if empty.
 * Never raw `StreamInfo.relatedItems`. Bilibili keeps the extractor list (YouTube feed unaware).
 */
fun HistoryDbHelper.nativeRelatedVideos(
    info: StreamInfo,
    serviceId: Int,
): List<StreamInfoItem> {
    val videoId = LocalServerMedia.getVideoId(info.url)
    val repo = youTubeRepository()
    val shortsEnabled = !nativeHideShorts()
    val fromStreamInfo = repo.getRelatedVideosFromStreamInfo(info)
    val fromInnerTube =
        if (serviceId.isYouTubeServiceId) {
            runBlocking {
                runCatching { withTimeoutOrNull(RELATED_TIMEOUT_MS) { repo.getRelatedCandidates(videoId) } }.getOrNull().orEmpty()
            }
        } else {
            emptyList()
        }
    val selected =
        PlayerRelatedVideosPolicy.select(
            videoId,
            primary = fromInnerTube,
            fallback = fromStreamInfo,
            current = emptyList(),
            shortsEnabled = shortsEnabled,
        )
    return selected.map { it.toStreamInfoItem(serviceId) }
}

private const val RELATED_TIMEOUT_MS = 10_000L
