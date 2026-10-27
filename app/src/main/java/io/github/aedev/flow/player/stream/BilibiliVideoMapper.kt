package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliChannelVideo
import io.github.aedev.flow.bilibili.BilibiliLiveItem
import io.github.aedev.flow.bilibili.BilibiliLiveRoom
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.bilibili.BilibiliRelated
import io.github.aedev.flow.bilibili.BilibiliSearchItem
import io.github.aedev.flow.bilibili.BilibiliVideoInfo
import io.github.aedev.flow.data.model.Video
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Bilibili counterpart of [InnerTubeVideoMapper]: native metadata → [Video]. */
object BilibiliVideoMapper {
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)

    /** Related-lane entry; ids carry "?p=1" like all Bilibili screen ids. */
    fun videoFromRelated(item: BilibiliRelated): Video =
        Video(
            id = "${item.bvid}?p=1",
            title = item.title,
            channelName = item.uploader.name,
            channelId =
                item.uploader.mid
                    .takeIf { it > 0 }
                    ?.toString()
                    .orEmpty(),
            thumbnailUrl = item.thumbnailUrl,
            duration = item.durationSec,
            viewCount = item.viewCount,
            uploadDate = item.uploadTimeSec.takeIf { it > 0 }?.let { DATE.format(Instant.ofEpochSecond(it)) } ?: "Unknown",
            channelThumbnailUrl = item.uploader.avatarUrl,
            tags = tagsOf(item.category),
            serviceId = BILIBILI_SERVICE_ID,
        )

    /** Uploader video-list row; list lacks uploader identity. */
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
            channelId =
                item.uploader.mid
                    .takeIf { it > 0 }
                    ?.toString()
                    .orEmpty(),
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
     * [videoId]: screen id ("BV...?p=N"), kept for queue/history/preload matching; [fallback]: prior screen data when Bilibili has none.
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
            channelId =
                info.uploader.mid
                    .takeIf { it > 0 }
                    ?.toString() ?: fallback?.channelId.orEmpty(),
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

    /** Live room as [Video]; [videoId] = screen id ("live:<roomId>"), kept. */
    fun videoFromLive(
        videoId: String,
        room: BilibiliLiveRoom,
        fallback: Video? = null,
    ): Video =
        Video(
            id = videoId,
            title = room.title.ifBlank { fallback?.title.orEmpty() },
            channelName = room.uploader.name.ifBlank { fallback?.channelName.orEmpty() },
            channelId =
                room.uploader.mid
                    .takeIf { it > 0 }
                    ?.toString() ?: fallback?.channelId.orEmpty(),
            thumbnailUrl = room.coverUrl.ifBlank { fallback?.thumbnailUrl.orEmpty() },
            duration = 0,
            viewCount = room.viewerCount,
            uploadDate = "",
            channelThumbnailUrl = room.uploader.avatarUrl.ifBlank { fallback?.channelThumbnailUrl.orEmpty() },
            tags = room.tags,
            isLive = true,
            serviceId = BILIBILI_SERVICE_ID,
        )

    /** Live room from a list (recommendations, search); playing now: no duration. */
    fun videoFromLiveItem(item: BilibiliLiveItem): Video =
        Video(
            id = BilibiliRef.Live(item.roomId).id,
            title = item.title,
            channelName = item.uploaderName,
            channelId = "",
            thumbnailUrl = item.coverUrl,
            duration = 0,
            viewCount = item.viewerCount,
            uploadDate = "",
            channelThumbnailUrl = item.uploaderAvatarUrl,
            isLive = true,
            serviceId = BILIBILI_SERVICE_ID,
        )

    /** Subject tags for the recommender: partition + uploader tags. */
    private fun tagsOf(
        category: String,
        tags: List<String> = emptyList(),
    ): List<String> = (listOf(category) + tags).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}
