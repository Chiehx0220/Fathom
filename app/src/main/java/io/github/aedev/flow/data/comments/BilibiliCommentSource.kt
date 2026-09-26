package io.github.aedev.flow.data.comments

import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliComment
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.data.model.Comment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Bilibili's comments in the shape [CommentsPager] pages through: the continuation of the main list
 * is Bilibili's own offset string, and the one of a reply list is the next page number.
 */
internal class BilibiliCommentSource(
    private val api: BilibiliApi,
) : CommentsSource {
    override suspend fun first(
        videoId: String,
        sortToken: String?,
    ): CommentsPageResult = more(videoId, continuation = "")

    override suspend fun more(
        videoId: String,
        continuation: String,
    ): CommentsPageResult {
        val page = api.comments(bvidOf(videoId), continuation)
        return CommentsPageResult(comments = page.comments.map { it.toComment() }, continuation = page.nextOffset)
    }

    override suspend fun replies(
        videoId: String,
        commentId: String,
        continuation: String,
    ): CommentsPageResult {
        val page = api.commentReplies(bvidOf(videoId), commentId, continuation.toIntOrNull() ?: 1)
        return CommentsPageResult(
            comments = page.replies.map { it.toComment(hasReplies = false) },
            continuation = page.nextPage?.toString(),
        )
    }

    private fun bvidOf(videoId: String) = BilibiliVideoId.parse(videoId).first

    private fun BilibiliComment.toComment(hasReplies: Boolean = true) =
        Comment(
            id = id,
            author = authorName,
            authorThumbnail = authorAvatarUrl,
            text = text,
            likeCount = likeCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            publishedTime = formatTime(postedSec),
            replyCount = replyCount,
            // Page 1 of the replies; the pager swaps it for the next page as they load.
            continuationToken = if (hasReplies && replyCount > 0) "1" else null,
            isPinned = isPinned,
            authorChannelId = authorMid.takeIf { it > 0 }?.toString().orEmpty(),
            isHearted = isLikedByUploader,
        )

    private fun formatTime(seconds: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
            .format(Date(seconds * 1000))
}
