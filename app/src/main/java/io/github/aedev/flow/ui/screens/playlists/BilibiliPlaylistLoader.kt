package io.github.aedev.flow.ui.screens.playlists

import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliChannelVideo
import io.github.aedev.flow.bilibili.BilibiliPlaylistId
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import org.schabi.newpipe.extractor.ServiceList
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Loads a whole Bilibili series or season, following its 30-per-page listing to the end. */
internal object BilibiliPlaylistLoader {
    private const val MAX_PAGES = 40
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)

    suspend fun load(
        api: BilibiliApi,
        playlistId: String,
        ref: BilibiliPlaylistId.Parsed,
    ): Playlist {
        val uploader = runCatching { api.channelInfo(ref.mid) }.getOrNull()
        val videos = mutableListOf<BilibiliChannelVideo>()
        var total = 0
        var page = 1
        while (page <= MAX_PAGES) {
            val result = api.playlistVideos(ref.kind, ref.mid, ref.id, page)
            total = result.total
            if (result.videos.isEmpty()) break
            videos += result.videos
            if (total in 1..videos.size) break
            page++
        }
        val mapped =
            videos.distinctBy { it.bvid }.map { video ->
                Video(
                    id = "${video.bvid}?p=1",
                    title = video.title,
                    channelName = video.authorName.ifBlank { uploader?.name.orEmpty() },
                    channelId = ref.mid.toString(),
                    thumbnailUrl = video.thumbnailUrl,
                    duration = video.durationSec,
                    viewCount = video.viewCount,
                    uploadDate = video.uploadTimeSec.takeIf { it > 0 }?.let { DATE.format(Instant.ofEpochSecond(it)) } ?: "",
                    channelThumbnailUrl = uploader?.avatarUrl.orEmpty(),
                    serviceId = ServiceList.BiliBili.serviceId,
                )
            }
        return Playlist(
            id = playlistId,
            name = ref.name,
            thumbnailUrl = mapped.firstOrNull()?.thumbnailUrl.orEmpty(),
            videoCount = mapped.size,
            videos = mapped,
            isLocal = false,
            serviceId = ServiceList.BiliBili.serviceId,
        )
    }
}
