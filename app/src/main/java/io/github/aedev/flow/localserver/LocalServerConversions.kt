package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.ui.screens.home.HomeFeedSources
import kotlinx.coroutines.flow.first
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import io.github.aedev.flow.data.model.Video as FlowVideo

// Home-feed candidates/ranking come from native `YouTubeRepository`/`FlowNeuroEngine` directly,
// not a ported copy. `toFlowVideo()`/`toStreamInfoItem()` convert at the boundary between
// NewPipeExtractor's [InfoItem] hierarchy (Local Server's native type across every page) and
// Flow's video-only [FlowVideo] display model.

// Singleton shared with native (same instance as the Hilt provider).
internal fun HistoryDbHelper.youTubeRepository(): YouTubeRepository = YouTubeRepository.getInstance(PlayerPreferences(appContext))

// Singleton reached through LocalServerEntryPoint, since this runs outside Hilt.
internal fun HistoryDbHelper.homeFeedSources(): HomeFeedSources = localServerEntryPoint(appContext).homeFeedSources()

// The subscription store is local, so this never touches the network.
internal suspend fun HistoryDbHelper.storedSubscriptionFeed(): List<FlowVideo> =
    runCatching { localServerEntryPoint(appContext).subscriptionFeedRepository().observeFeed().first() }.getOrDefault(emptyList())

@Volatile
private var flowNeuroInitialized = false

internal suspend fun HistoryDbHelper.ensureFlowNeuroInitialized() {
    if (flowNeuroInitialized) return
    FlowNeuroEngine.initialize(appContext)
    flowNeuroInitialized = true
}

// serviceId is unused: this.serviceId is authoritative; the parameter keeps the converters symmetric.

/**
 * Listing candidates (StreamInfoItem) carry no tags/description - only the watch-page StreamInfo
 * overload below has those.
 */
fun StreamInfoItem.toFlowVideo(
    @Suppress("UNUSED_PARAMETER") serviceId: Int,
): FlowVideo {
    val durationSeconds = this.duration.coerceAtLeast(0).toInt()
    return FlowVideo(
        id = LocalServerMedia.getVideoId(this.url),
        title = this.name ?: "",
        channelName = this.uploaderName ?: this.name ?: "",
        channelId = channelUrlToId(this.uploaderUrl) ?: "",
        thumbnailUrl = HtmlRendererCommon.getThumbnailUrl(this.thumbnailUrl),
        duration = durationSeconds,
        viewCount = this.viewCount.coerceAtLeast(-1),
        likeCount = 0,
        uploadDate = this.textualUploadDate ?: "",
        description = "",
        channelThumbnailUrl = HtmlRendererCommon.getThumbnailUrl(this.uploaderAvatarUrl),
        tags = emptyList(),
        isLive = this.streamType == StreamType.LIVE_STREAM || this.streamType == StreamType.AUDIO_LIVE_STREAM,
        isShort = durationSeconds in 1..120,
        serviceId = this.serviceId,
    )
}

/** Full watch-page detail (StreamInfo) - includes description/tags, unlike StreamInfoItem. */
fun StreamInfo.toFlowVideo(
    @Suppress("UNUSED_PARAMETER") serviceId: Int,
): FlowVideo {
    val durationSeconds = this.duration.coerceAtLeast(0).toInt()
    return FlowVideo(
        id = LocalServerMedia.getVideoId(this.url),
        title = this.name ?: "",
        channelName = this.uploaderName ?: "",
        channelId = channelUrlToId(this.uploaderUrl) ?: "",
        thumbnailUrl = HtmlRendererCommon.getThumbnailUrl(this.thumbnails),
        duration = durationSeconds,
        viewCount = this.viewCount.coerceAtLeast(-1),
        likeCount = this.likeCount.coerceAtLeast(0),
        uploadDate = this.textualUploadDate ?: "",
        description = this.description?.content ?: "",
        channelThumbnailUrl = HtmlRendererCommon.getThumbnailUrl(this.uploaderAvatars),
        tags = this.tags ?: emptyList(),
        isLive = this.streamType == StreamType.LIVE_STREAM || this.streamType == StreamType.AUDIO_LIVE_STREAM,
        isShort = durationSeconds in 1..120,
        serviceId = this.serviceId,
    )
}

/** Converts [FlowVideo] to the [InfoItem] shape `HtmlRenderer*` expects. */
fun FlowVideo.toStreamInfoItem(serviceId: Int): StreamInfoItem {
    val item = StreamInfoItem(serviceId, videoIdToUrl(this.id, serviceId), this.title, StreamType.VIDEO_STREAM)
    item.setUploaderName(this.channelName)
    item.setUploaderUrl(if (this.channelId.isNotEmpty()) channelIdToUrl(this.channelId, serviceId) else "")
    if (this.thumbnailUrl.isNotEmpty()) {
        item.thumbnailUrl = this.thumbnailUrl
    }
    if (this.channelThumbnailUrl.isNotEmpty()) {
        item.uploaderAvatarUrl = this.channelThumbnailUrl
    }
    item.setDuration(this.duration.toLong())
    item.setViewCount(this.viewCount)
    item.setTextualUploadDate(this.uploadDate)
    if (this.description.isNotEmpty()) item.setShortDescription(this.description)
    return item
}
