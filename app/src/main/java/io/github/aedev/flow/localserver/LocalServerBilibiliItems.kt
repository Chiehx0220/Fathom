package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliChannelVideo
import io.github.aedev.flow.bilibili.BilibiliComment
import io.github.aedev.flow.bilibili.BilibiliLiveItem
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.content.CommentsInfoItem
import io.github.aedev.flow.content.DateWrapper
import io.github.aedev.flow.content.Description
import io.github.aedev.flow.content.Page
import io.github.aedev.flow.content.StreamInfoItem
import io.github.aedev.flow.player.stream.model.StreamType
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Bilibili rows as content-model item types.

/** List video row, from fields every Bilibili list carries. */
internal fun bilibiliVideoItem(
    id: String,
    title: String,
    thumbnailUrl: String,
    durationSec: Int,
    views: Long,
    uploaderName: String,
    uploaderMid: Long,
    uploaderAvatarUrl: String,
): StreamInfoItem =
    StreamInfoItem(LocalServerBilibili.serviceId, LocalServerBilibili.videoUrl(id), title, StreamType.VIDEO_STREAM).apply {
        this.thumbnailUrl = thumbnailUrl
        this.uploaderName = uploaderName
        uploaderUrl = if (uploaderMid > 0) LocalServerBilibili.channelUrl(uploaderMid.toString()) else ""
        this.uploaderAvatarUrl = uploaderAvatarUrl
        duration = durationSec.toLong()
        viewCount = views
    }

/** Live room row: opens the room, shown live. */
internal fun bilibiliLiveItem(room: BilibiliLiveItem): StreamInfoItem =
    StreamInfoItem(
        LocalServerBilibili.serviceId,
        BilibiliRef.Live(room.roomId).url,
        room.title,
        StreamType.LIVE_STREAM,
    ).apply {
        thumbnailUrl = room.coverUrl
        uploaderName = room.uploaderName
        uploaderAvatarUrl = room.uploaderAvatarUrl
        viewCount = room.viewerCount
    }

/** Uploader video-list row; name/avatar from caller (list has neither). */
internal fun BilibiliChannelVideo.toStreamItem(
    uploaderName: String,
    uploaderAvatarUrl: String,
    uploaderUrl: String,
): StreamInfoItem {
    // Read up front: inside apply, the item's own same-named properties win.
    val video = this
    return StreamInfoItem(LocalServerBilibili.serviceId, LocalServerBilibili.videoUrl("$bvid?p=1"), title, StreamType.VIDEO_STREAM).apply {
        thumbnailUrl = video.thumbnailUrl
        this.uploaderName = uploaderName.ifEmpty { video.authorName }
        this.uploaderUrl = uploaderUrl
        this.uploaderAvatarUrl = uploaderAvatarUrl
        duration = video.durationSec.toLong()
        viewCount = video.viewCount
        if (video.uploadTimeSec > 0) {
            textualUploadDate = formatBilibiliDate(video.uploadTimeSec)
            uploadDate = DateWrapper(OffsetDateTime.ofInstant(Instant.ofEpochSecond(video.uploadTimeSec), ZoneOffset.UTC))
        }
    }
}

/** Comment; [hasReplies] false for a reply (none of its own). */
internal fun BilibiliComment.toCommentItem(
    videoUrl: String,
    hasReplies: Boolean,
): CommentsInfoItem {
    val comment = this
    return CommentsInfoItem(LocalServerBilibili.serviceId, videoUrl, authorName).apply {
        commentId = comment.id
        commentText = Description(comment.text, Description.PLAIN_TEXT)
        uploaderName = comment.authorName
        uploaderUrl = if (comment.authorMid > 0) LocalServerBilibili.channelUrl(comment.authorMid.toString()) else ""
        uploaderAvatarUrl = comment.authorAvatarUrl
        likeCount = comment.likeCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        textualUploadDate = formatBilibiliDate(comment.postedSec)
        isPinned = comment.isPinned
        isHeartedByUploader = comment.isLikedByUploader
        replyCount = comment.replyCount
        if (hasReplies && comment.replyCount > 0) replies = Page("r:${comment.id}:1")
    }
}

private fun formatBilibiliDate(seconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
        .format(Date(seconds * 1000))
