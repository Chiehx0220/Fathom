package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.HomeFeedCacheFilters
import io.github.aedev.flow.data.recommendation.FeedExclusions
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.explore.chartsCountryOrFallback
import io.github.aedev.flow.player.PlayerRelatedVideosPolicy
import io.github.aedev.flow.ui.screens.home.FeedTasteProfile
import io.github.aedev.flow.ui.screens.home.GraphCandidate
import io.github.aedev.flow.ui.screens.home.assembleHomeFeed
import io.github.aedev.flow.ui.screens.home.buildHomeFeedLanes
import io.github.aedev.flow.ui.screens.home.demoteByFit
import io.github.aedev.flow.ui.screens.home.dynamicFreshSubSlots
import io.github.aedev.flow.ui.screens.home.enrichAvatars
import io.github.aedev.flow.ui.screens.home.feedTasteProfile
import io.github.aedev.flow.ui.screens.home.filterValid
import io.github.aedev.flow.ui.screens.home.filterWatched
import io.github.aedev.flow.ui.screens.home.spaceByChannel
import io.github.aedev.flow.ui.screens.home.storedSubscriptionVideos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import io.github.aedev.flow.data.model.Video as FlowVideo

// 5-minute cache of the assembled home feed, keyed by serviceId + feedMode.
private const val HOME_FEED_CACHE_TTL_MS = 5 * 60 * 1000L

private data class HomeFeedCacheEntry(
    val items: List<InfoItem>,
    val timestampMs: Long,
)

private val homeFeedCache = java.util.concurrent.ConcurrentHashMap<String, HomeFeedCacheEntry>()

/** One round of FlowNeuro discovery queries, fetched concurrently. `resetDepth`: fresh session
 * vs. load-more. Query count matches native's wave-1. Shared by [buildAndRankHomeFeed] and
 * [continueDiscoveryFeed]. */
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

/** Shared inputs for the home-feed builders: watched-video gating (`hideWatchedVideosFromHome`),
 * FlowNeuroEngine block/suppress lists, brain snapshot + taste profile, subscribed-channel
 * avatars. */
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

/** Dedupes by video id, ranks via FlowNeuroEngine (falls back to unranked order on failure),
 * converts to [StreamInfoItem]. Used by [continueDiscoveryFeed]. */
private suspend fun HistoryDbHelper.rankAndConvert(
    videos: List<FlowVideo>,
    serviceId: Int,
): List<StreamInfoItem> {
    val deduped = LinkedHashMap<String, FlowVideo>()
    // Only this page's serviceId: watch and channel links are built for one service.
    for (v in videos) if (v.id.isNotBlank() && v.serviceId == serviceId) deduped.putIfAbsent(v.id, v)
    if (deduped.isEmpty()) return emptyList()

    val subIds = subscriptionRepository().getAllSubscriptionIds()
    val ranked =
        runCatching { FlowNeuroEngine.rank(deduped.values.toList(), subIds) }
            .getOrDefault(deduped.values.toList())
    return ranked.map { it.toStreamInfoItem(serviceId) }
}

/**
 * Assembles/ranks the home feed via the SAME pipeline as native's wave-1: [buildHomeFeedLanes]/
 * [assembleHomeFeed] from `io.github.aedev.flow.ui.screens.home`, not a ported copy. Gets
 * quota-balanced source blending, same-channel spacing, duration/format-fit demotion,
 * fresh-upload pinning, and the related-video (/next graph) lane.
 *
 * UPSTREAM COUPLING: [buildHomeFeedLanes], [assembleHomeFeed], [feedTasteProfile], [filterValid],
 * [filterWatched], [demoteByFit], [spaceByChannel], [dynamicFreshSubSlots], [enrichAvatars] are
 * upstream-owned (`HomeFeedAssembly.kt`/`HomeFeedFilters.kt`/`HomeFeedRanking.kt`, PR #1040). A
 * signature/behavior change there on upstream sync won't surface as a merge conflict (different
 * file) - re-verify this function compiles and the home feed renders after every sync.
 *
 * `rssFeed`/`homeFeedSources()`/`youTubeRepository()` resolve to the SAME Hilt singletons native
 * Home uses (via [localServerEntryPoint]/[YouTubeRepository.getInstance]), not separate instances.
 *
 * Deliberate divergence from native:
 * - YouTube-only; no Bilibili lane.
 * - No wave-2 enrichment / persistent feed cache / reserve page (those smooth a long-lived
 *   Compose screen, not a single request/response cycle).
 *
 * Second return value: whether [continueDiscoveryFeed] has more to fetch. No NewPipeExtractor
 * Page involved - `YoutubeTrendingExtractor` has no continuation, so "load more" is a deeper
 * discovery-query round.
 */
fun HistoryDbHelper.buildAndRankHomeFeed(
    serviceId: Int,
    feedMode: String,
): Pair<List<InfoItem>, Boolean> =
    runBlocking {
        val cacheKey = "$serviceId:$feedMode"
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

        // Home has no separate trending lane any more, so trending joins discovery.
        val rawSubs = if (feedMode == "mix" && subIds.isNotEmpty()) rssFeed.storedSubscriptionVideos(now) else emptyList()
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

        // Subscriptions and history span all services; filter to this page's serviceId first (ServiceIdHelpers.kt).
        val result = mix.videos.filter { it.serviceId == serviceId }.map { it.toStreamInfoItem(serviceId) }
        if (result.isEmpty()) return@runBlocking emptyList<InfoItem>() to false

        homeFeedCache[cacheKey] = HomeFeedCacheEntry(result, now)
        result to true
    }

/** `feedMode == "subs"`: the stored subscription uploads via `filterValid`/`filterWatched`/`demoteByFit`/
 * `spaceByChannel`, but NOT through [buildHomeFeedLanes]/[assembleHomeFeed] - that pipeline caps
 * the subs lane at 15 and the mix at 40 (`HOME_TARGET_SIZE`), which would truncate this mode's
 * "just my subscriptions" contract. Deliberate, not an oversight. */
fun HistoryDbHelper.buildSubsOnlyFeed(serviceId: Int): List<InfoItem> =
    runBlocking {
        val cacheKey = "$serviceId:subs"
        val cached = homeFeedCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.timestampMs < HOME_FEED_CACHE_TTL_MS) {
            return@runBlocking cached.items
        }

        ensureFlowNeuroInitialized()
        val subIds = subscriptionRepository().getAllSubscriptionIds()
        if (subIds.isEmpty()) return@runBlocking emptyList()

        val context = buildHomeFeedContext()
        val pool =
            storedSubscriptionFeed()
                .storedSubscriptionVideos(now)
                .filter { it.serviceId == serviceId }
                .filterValid()
                .filterWatched(context.watched)
                .filterNot(context.exclusions::hidesFromRecommendations)
                .enrichAvatars(context.subAvatarMap)
        if (pool.isEmpty()) return@runBlocking emptyList()

        val ranked = runCatching { FlowNeuroEngine.rank(pool, subIds) }.getOrDefault(pool)
        val spaced = spaceByChannel(demoteByFit(ranked, context.taste), gap = 1)

        val result = spaced.map { it.toStreamInfoItem(serviceId) }
        homeFeedCache[cacheKey] = HomeFeedCacheEntry(result, now)
        result
    }

/** "Load more": deeper discovery-query round, ranked like [buildAndRankHomeFeed]. Uncached, no
 * subscription-feed re-pull (subs lane is first-load only, matching native). Shares
 * FlowNeuroEngine's discovery-depth counter with native. */
fun HistoryDbHelper.continueDiscoveryFeed(serviceId: Int): Pair<List<InfoItem>, Boolean> =
    runBlocking {
        ensureFlowNeuroInitialized()
        val repo = youTubeRepository()

        val videos = fetchDiscoveryVideos(repo, resetDepth = false)
        val result = rankAndConvert(videos, serviceId)
        result to result.isNotEmpty()
    }

// The Shorts feed is not served here. Native equivalents: ShortsRepository and ShortsDiscoveryEngine.

/** Trending kiosk, unranked - `handleApiHome()`'s raw JSON feed (vs. `handleApiRecommendations()`,
 * which uses [buildAndRankHomeFeed]'s ranked pool). No pagination (`YoutubeTrendingExtractor`
 * has no next page). */
fun HistoryDbHelper.fetchTrendingItems(serviceId: Int): List<StreamInfoItem> =
    runBlocking {
        trendingVideos().map { it.toStreamInfoItem(serviceId) }
    }

/** YouTube's trending chart for the user's trending region. */
private suspend fun HistoryDbHelper.trendingVideos(): List<FlowVideo> {
    val region = playerPreferences().trendingRegion.first()
    return YouTube
        .videoCharts("TRENDING_VIDEOS", chartsCountryOrFallback(region))
        .getOrNull()
        ?.entries
        .orEmpty()
}

/**
 * Reorders `items` via FlowNeuroEngine - never changes composition, only order. Falls back to
 * original order on failure.
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

/** Reports a click/watch/etc. signal to FlowNeuroEngine. Best-effort. */
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
 * Related videos via `PlayerRelatedVideosPolicy`, same as native's watch page - never raw
 * `StreamInfo.relatedItems`. `primary` is the extractor's own list (no network call); falls back
 * to `getRelatedCandidates()` (InnerTube `/next`) only if `primary` sanitizes to empty.
 */
fun HistoryDbHelper.nativeRelatedVideos(
    info: StreamInfo,
    serviceId: Int,
): List<StreamInfoItem> {
    val videoId = LocalServerMedia.getVideoId(info.url)
    val repo = youTubeRepository()
    val shortsEnabled = !nativeHideShorts()
    val primary = repo.getRelatedVideosFromStreamInfo(info)
    val sanitizedPrimary = PlayerRelatedVideosPolicy.sanitize(videoId, primary, shortsEnabled)
    val selected =
        if (sanitizedPrimary.isNotEmpty()) {
            sanitizedPrimary
        } else {
            val fallback =
                runBlocking {
                    runCatching { repo.getRelatedCandidates(videoId) }.getOrDefault(emptyList())
                }
            PlayerRelatedVideosPolicy.select(videoId, primary, fallback, current = emptyList(), shortsEnabled = shortsEnabled)
        }
    return selected.map { it.toStreamInfoItem(serviceId) }
}
