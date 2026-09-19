package io.github.aedev.flow.player.stream

import io.github.aedev.flow.bilibili.BilibiliRelated
import io.github.aedev.flow.bilibili.BilibiliVideoInfo
import io.github.aedev.flow.data.model.Video
import org.schabi.newpipe.extractor.ServiceList
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Bilibili counterpart of [InnerTubeVideoMapper]: the native client's metadata as a [Video]. */
object BilibiliVideoMapper {
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)

    /**
     * [videoId] is the id the screen already uses for this video ("BV...?p=N"), kept as is so the
     * queue, history and preload bookkeeping keep matching it; [fallback] supplies whatever the
     * screen knew before this load (a search result's thumbnail, say) when Bilibili has nothing.
     */
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
            serviceId = ServiceList.BiliBili.serviceId,
        )

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
            serviceId = ServiceList.BiliBili.serviceId,
        )
}
