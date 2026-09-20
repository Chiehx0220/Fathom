package org.schabi.newpipe.localserver

import io.github.aedev.flow.bilibili.BilibiliChannelVideo
import io.github.aedev.flow.bilibili.BilibiliComment
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Bilibili's rows as the NewPipe item types the local server's pages are built from.

/** A video row of a list, from the fields every Bilibili list carries. */
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
        setUploaderName(uploaderName)
        setUploaderUrl(if (uploaderMid > 0) LocalServerBilibili.channelUrl(uploaderMid.toString()) else "")
        this.uploaderAvatarUrl = uploaderAvatarUrl
        setDuration(durationSec.toLong())
        setViewCount(views)
    }

/** A row of an uploader's video list; the name and avatar come from the caller, the list has neither. */
internal fun BilibiliChannelVideo.toStreamItem(
    uploaderName: String,
    uploaderAvatarUrl: String,
    uploaderUrl: String,
): StreamInfoItem {
    // Read up front: inside apply, the item's own properties of the same names win.
    val video = this
    return StreamInfoItem(LocalServerBilibili.serviceId, LocalServerBilibili.videoUrl("$bvid?p=1"), title, StreamType.VIDEO_STREAM).apply {
        thumbnailUrl = video.thumbnailUrl
        setUploaderName(uploaderName.ifEmpty { video.authorName })
        setUploaderUrl(uploaderUrl)
        this.uploaderAvatarUrl = uploaderAvatarUrl
        setDuration(video.durationSec.toLong())
        setViewCount(video.viewCount)
        if (video.uploadTimeSec > 0) setTextualUploadDate(formatBilibiliDate(video.uploadTimeSec))
    }
}

/** A comment; [hasReplies] is false for a reply, which has none of its own to open. */
internal fun BilibiliComment.toCommentItem(
    videoUrl: String,
    hasReplies: Boolean,
): CommentsInfoItem {
    val comment = this
    return CommentsInfoItem(LocalServerBilibili.serviceId, videoUrl, authorName).apply {
        setCommentId(comment.id)
        setCommentText(Description(comment.text, Description.PLAIN_TEXT))
        setUploaderName(comment.authorName)
        setUploaderUrl(if (comment.authorMid > 0) LocalServerBilibili.channelUrl(comment.authorMid.toString()) else "")
        uploaderAvatarUrl = comment.authorAvatarUrl
        setLikeCount(comment.likeCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        setTextualUploadDate(formatBilibiliDate(comment.postedSec))
        setPinned(comment.isPinned)
        setHeartedByUploader(comment.isLikedByUploader)
        setReplyCount(comment.replyCount)
        if (hasReplies && comment.replyCount > 0) setReplies(Page("r:${comment.id}:1"))
    }
}

private fun formatBilibiliDate(seconds: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
        .format(Date(seconds * 1000))
