package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliDanmakuPosition
import kotlinx.serialization.json.JsonElement
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

// Extractor results → wire shapes. `channelId` = full channel URL.

internal const val SHORT_MAX_SECONDS = 120

internal fun BilibiliDanmakuPosition.webName() =
    when (this) {
        BilibiliDanmakuPosition.TOP -> "top"
        BilibiliDanmakuPosition.BOTTOM -> "bottom"
        BilibiliDanmakuPosition.SCROLL -> "scroll"
    }

private fun StreamType?.isLive() = this == StreamType.LIVE_STREAM || this == StreamType.AUDIO_LIVE_STREAM

/** Empty without real avatar (client draws initial). */
private fun realAvatar(
    hasAvatar: Boolean,
    url: () -> String,
) = if (hasAvatar) sameOriginImageUrl(url()) else ""

internal fun Page?.token(): String? = PageToken.encode(this)

internal fun InfoItem.toVideoCard(): VideoCard {
    val stream = this as? StreamInfoItem
    return VideoCard(
        id = LocalServerMedia.getVideoId(url),
        url = url ?: "",
        serviceId = serviceId,
        title = name ?: "",
        channelName = stream?.uploaderName ?: name ?: "",
        channelId = stream?.uploaderUrl ?: "",
        thumbnailUrl = WebFormat.getThumbnailUrl(thumbnailUrl),
        channelThumbnailUrl =
            realAvatar(WebFormat.hasThumbnail(stream?.uploaderAvatarUrl)) {
                WebFormat.getThumbnailUrl(stream?.uploaderAvatarUrl)
            },
        duration = stream?.duration?.coerceAtLeast(0)?.toInt() ?: 0,
        viewCount = stream?.viewCount?.coerceAtLeast(-1) ?: -1,
        uploadDate = stream?.textualUploadDate ?: "",
        isLive = stream?.streamType.isLive(),
        isShort = stream != null && stream.duration in 1..SHORT_MAX_SECONDS,
    )
}

internal fun ChannelInfoItem.toCard() =
    ChannelCard(
        id = url,
        serviceId = serviceId,
        name = name ?: "",
        thumbnailUrl = sameOriginImageUrl(WebFormat.getThumbnailUrl(thumbnailUrl)),
        subscriberCount = subscriberCount.coerceAtLeast(-1),
        url = url,
    )

internal fun PlaylistInfoItem.toCard() =
    PlaylistCard(
        id = url,
        serviceId = serviceId,
        name = name ?: "",
        thumbnailUrl = WebFormat.getThumbnailUrl(thumbnailUrl),
        videoCount = streamCount.coerceAtLeast(0).toInt(),
        isLocal = false,
    )

/** One card per item, by kind. */
internal fun List<InfoItem>.toCards(): List<JsonElement> =
    map {
        when (it) {
            is ChannelInfoItem -> ApiJson.encodeToJsonElement(ChannelCard.serializer(), it.toCard())
            is PlaylistInfoItem -> ApiJson.encodeToJsonElement(PlaylistCard.serializer(), it.toCard())
            else -> ApiJson.encodeToJsonElement(VideoCard.serializer(), it.toVideoCard())
        }
    }

internal fun List<InfoItem>.toSearchResult(
    next: Page?,
    hasMore: Boolean? = null,
): SearchResult =
    SearchResult(
        videos = filter { it !is ChannelInfoItem && it !is PlaylistInfoItem }.map { it.toVideoCard() },
        channels = filterIsInstance<ChannelInfoItem>().map { it.toCard() },
        playlists = filterIsInstance<PlaylistInfoItem>().map { it.toCard() },
        nextPage = next.token(),
        hasMore = hasMore,
    )

internal fun ChannelHeader.toDto(isSubscribed: Boolean) =
    ChannelHeaderDto(
        id = url,
        name = name,
        thumbnailUrl = sameOriginImageUrl(WebFormat.getThumbnailUrl(avatarUrl)),
        subscriberCount = subscriberCount.coerceAtLeast(-1),
        description = description,
        isSubscribed = isSubscribed,
        url = url,
    )

internal fun CommentsInfoItem.toDto() =
    CommentDto(
        id = commentId ?: "",
        author = uploaderName ?: "",
        authorThumbnail = realAvatar(WebFormat.hasThumbnail(uploaderAvatarUrl)) { WebFormat.getThumbnailUrl(uploaderAvatarUrl) },
        text = commentText.content ?: "",
        textType = if (commentText.type == Description.HTML) "html" else "text",
        likeCount = likeCount.coerceAtLeast(0),
        publishedTime = textualUploadDate ?: "",
        isPinned = isPinned,
        authorChannelId = uploaderUrl ?: "",
        replyCount = replyCount.coerceAtLeast(0),
        continuationToken = replies.token(),
    )
