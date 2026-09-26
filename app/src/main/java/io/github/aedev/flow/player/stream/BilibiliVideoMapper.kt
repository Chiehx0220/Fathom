package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliChannelVideo
import io.github.aedev.flow.bilibili.BilibiliRelated
import io.github.aedev.flow.bilibili.BilibiliSearchItem
import io.github.aedev.flow.bilibili.BilibiliVideoInfo
import io.github.aedev.flow.data.model.Video
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Bilibili counterpart of [InnerTubeVideoMapper]: the native client's metadata as a [Video]. */
object BilibiliVideoMapper {
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)

    /** A related-lane entry; ids carry "?p=1" like every other Bilibili id the screen holds. */
    fun videoFromRelated(item: BilibiliRelated): Video =
        Video(
            id = "${item.bvid}?p=1",
            title = item.title,
            channelName = item.uploader.name,
            channelId = item.uploader.mid.takeIf { it > 0 }?.toString().orEmpty(),
            thumbnailUrl = item.thumbnailUrl,
            duration = item.durationSec,
            viewCount = item.viewCount,
            uploadDate = item.uploadTimeSec.takeIf { it > 0 }?.let { DATE.format(Instant.ofEpochSecond(it)) } ?: "Unknown",
            channelThumbnailUrl = item.uploader.avatarUrl,
            tags = tagsOf(item.category),
            serviceId = BILIBILI_SERVICE_ID,
        )

    /** A row of an uploader's video list; the list itself does not say who the uploader is. */
    fun videoFromChannel(
        video: BilibiliChannelVideo,
        mid: Long,
        channelName: String,
        channelAvatarUrl: String,
    ): Video =
        Video(
            id = "${video.bvid}?p=1",
            title = video.title,
            thumbnailUrl = video.thumbnailUrl,
            channelName = video.authorName.ifBlank { channelName },
            channelId = mid.toString(),
            channelThumbnailUrl = channelAvatarUrl,
            viewCount = video.viewCount,
            duration = video.durationSec,
            uploadDate = video.uploadTimeSec.takeIf { it > 0 }?.let { DATE.format(Instant.ofEpochSecond(it)) } ?: "",
            timestamp = video.uploadTimeSec * 1000,
            serviceId = BILIBILI_SERVICE_ID,
        )

    fun videoFromSearch(item: BilibiliSearchItem.Video): Video =
        Video(
            id = "${item.bvid}?p=1",
            title = item.title,
            channelName = item.uploader.name,
            channelId = item.uploader.mid.takeIf { it > 0 }?.toString().orEmpty(),
            thumbnailUrl = item.thumbnailUrl,
            duration = item.durationSec,
            viewCount = item.viewCount,
            uploadDate = item.uploadTimeSec.takeIf { it > 0 }?.let { DATE.format(Instant.ofEpochSecond(it)) } ?: "",
            timestamp = System.currentTimeMillis(),
            channelThumbnailUrl = item.uploader.avatarUrl,
            channelThumbnailUrls = listOfNotNull(item.uploader.avatarUrl.takeIf { it.isNotBlank() }),
            tags = tagsOf(item.category, item.tags),
            serviceId = BILIBILI_SERVICE_ID,
        )

    /**
     * [videoId] is the id the screen already uses for this video ("BV...?p=N"), kept as is so the
     * queue, history and preload bookkeeping keep matching it; [fallback] supplies whatever the
     * screen knew before this load (a search result's thumbnail, say) when Bilibili has nothing.
     */
    fun videoFromInfo(
        videoId: String,
        info: BilibiliVideoInfo,
        fallback: Video? = null,
    ): Video =
        Video(
            id = videoId,
            title = info.title.ifBlank { fallback?.title.orEmpty() },
            channelName = info.uploader.name.ifBlank { fallback?.channelName.orEmpty() },
            channelId = info.uploader.mid.takeIf { it > 0 }?.toString() ?: fallback?.channelId.orEmpty(),
            thumbnailUrl = info.thumbnailUrl.ifBlank { fallback?.thumbnailUrl.orEmpty() },
            duration = info.durationSec,
            viewCount = info.viewCount,
            likeCount = info.likeCount,
            uploadDate =
                info.uploadTimeSec.takeIf { it > 0 }?.let { DATE.format(Instant.ofEpochSecond(it)) }
                    ?: fallback?.uploadDate
                    ?: "Unknown",
            description = info.description,
            channelThumbnailUrl = info.uploader.avatarUrl.ifBlank { fallback?.channelThumbnailUrl.orEmpty() },
            tags = tagsOf(info.category).ifEmpty { fallback?.tags.orEmpty() },
            serviceId = BILIBILI_SERVICE_ID,
        )

    /**
     * What Bilibili knows about a video's subject, as the tags the recommender learns from: the
     * partition it is filed under and the uploader's own tags.
     */
    private fun tagsOf(
        category: String,
        tags: List<String> = emptyList(),
    ): List<String> = (listOf(category) + tags).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
