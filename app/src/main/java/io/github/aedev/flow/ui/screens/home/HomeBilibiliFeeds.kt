package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliChannelPageKey
import io.github.aedev.flow.bilibili.BilibiliSearchItem
import io.github.aedev.flow.bilibili.BilibiliSearchType
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.isCjk
import io.github.aedev.flow.player.stream.BilibiliVideoMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// The home feed's Bilibili lane, kept out of HomeViewModel.kt so upstream changes to that file's
// YouTube side merge without touching it.

// Bilibili's search is a per-query network round trip, so wave 1 only spends this many queries on it.
private const val BILIBILI_DISCOVERY_QUERY_LIMIT = 2

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

internal data class BilibiliWave1Feeds(
    val subs: List<Video>,
    val discovery: List<Video>,
    val viral: List<Video>,
)

// Bilibili's three wave-1 fetches (subs, discovery, trending), factored out of loadFlowFeed()'s
// supervisorScope to reduce merge-conflict surface with upstream's YouTube-side fetch logic there.
internal fun CoroutineScope.launchBilibiliWave1Feeds(
    subscriptionRepository: SubscriptionRepository,
    bilibili: BilibiliApi,
    discoveryQueries: List<String>,
): Deferred<BilibiliWave1Feeds> =
    async {
        // getAllSubscriptionIds() has no per-service info - filter to Bilibili channels here so
        // the fetch below routes through the right extractor.
        val bilibiliSubs =
            runCatching {
                subscriptionRepository
                    .getAllSubscriptions()
                    .first()
                    .filter { it.serviceId == BILIBILI_SERVICE_ID }
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
                                    bilibili
                                        .channelVideos(mid, BilibiliChannelPageKey(page = 1, lastAid = 0L))
                                        .videos
                                        .take(BILIBILI_VIDEOS_PER_CHANNEL)
                                        .map { BilibiliVideoMapper.videoFromChannel(it, mid, sub.channelName, sub.channelThumbnail) }
                                }.getOrDefault(emptyList())
                            }
                        }.awaitAll()
                        .flatten()
                        .sortedByDescending { it.timestamp }
                        .take(BILIBILI_SUBS_TOTAL_LIMIT)
                }
            }

        // Gives HomeContentSourceFilter real non-subscription Bilibili content, not just the
        // fresh-subs RSS lane.
        val deferredDiscovery =
            async {
                // Only the Chinese and Japanese interests: an English query finds little on Bilibili.
                discoveryQueries
                    .filter { query -> query.any(::isCjk) }
                    .take(BILIBILI_DISCOVERY_QUERY_LIMIT)
                    .map { query ->
                        async {
                            fetchSafely {
                                bilibili
                                    .search(query, BilibiliSearchType.VIDEO, page = 1)
                                    .items
                                    .filterIsInstance<BilibiliSearchItem.Video>()
                                    .map(BilibiliVideoMapper::videoFromSearch)
                            }
                        }
                    }.awaitAll()
                    .flatten()
            }

        val deferredViral =
            async {
                fetchSafely { bilibili.popular().map(BilibiliVideoMapper::videoFromRelated) }
            }

        BilibiliWave1Feeds(
            subs = deferredSubs.await(),
            discovery = deferredDiscovery.await(),
            viral = deferredViral.await(),
        )
    }
