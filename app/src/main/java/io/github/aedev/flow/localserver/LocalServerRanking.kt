package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.model.isYouTubeServiceId
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.player.PlayerRelatedVideosPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Collections
import java.util.IdentityHashMap
import io.github.aedev.flow.data.model.Video as FlowVideo

/** Reorders via FlowNeuroEngine; order only, original on failure. */
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

            val rankedSet = Collections.newSetFromMap(IdentityHashMap<InfoItem, Boolean>())
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

/** Related, watch-page order: InnerTube `/next`, extractor list only if empty ([PlayerRelatedVideosPolicy]); Bilibili keeps extractor list. */
fun HistoryDbHelper.relatedVideos(
    info: StreamInfo,
    serviceId: Int,
): List<StreamInfoItem> {
    val videoId = LocalServerMedia.getVideoId(info.url)
    val repo = youTubeRepository()
    val shortsEnabled = !hideShorts()
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
