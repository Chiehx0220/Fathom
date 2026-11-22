package io.github.aedev.flow.data.comments

import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliComment
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.data.model.Comment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Bilibili comments for [CommentsPager]; main list: offset string, replies: page number. */
internal class BilibiliCommentSource(
    apiProvider: () -> BilibiliApi,
) : CommentsSource {
    private val api by lazy(apiProvider)

    override suspend fun firstPage(
        videoId: String,
        sortToken: String?,
    ): CommentsPageResult = nextPage(videoId, continuation = "")

    override suspend fun nextPage(
        videoId: String,
        continuation: String,
    ): CommentsPageResult {
        // Live room: no comment section (danmaku layer is chat).
        if (BilibiliRef.Live.ofId(videoId) != null) return CommentsPageResult(comments = emptyList(), continuation = null)
        val page = api.comments(bvidOf(videoId), continuation)
        return CommentsPageResult(comments = page.comments.map { it.toComment() }, continuation = page.nextOffset)
    }

    /** [continuation] = "<comment id>:<page>"; reply API needs the parent id. */
    override suspend fun replies(
        videoId: String,
        continuation: String,
    ): CommentsPageResult {
        val commentId = continuation.substringBeforeLast(':')
        val page = api.commentReplies(bvidOf(videoId), commentId, continuation.substringAfterLast(':').toIntOrNull() ?: 1)
        return CommentsPageResult(
            comments = page.replies.map { it.toComment(hasReplies = false) },
            continuation = page.nextPage?.let { "$commentId:$it" },
        )
    }

    override suspend fun timed(
        videoId: String,
        sectionToken: String,
    ): CommentsPageResult = CommentsPageResult.EMPTY

    private fun bvidOf(videoId: String) = BilibiliRef.Video.parse(videoId).bvid

    private fun BilibiliComment.toComment(hasReplies: Boolean = true) =
        Comment(
            id = id,
            author = authorName,
            authorThumbnail = authorAvatarUrl,
            text = text,
            likeCount = likeCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            publishedTime = formatTime(postedSec),
            replyCount = replyCount,
            // Replies page 1; pager swaps in next pages.
            continuationToken = if (hasReplies && replyCount > 0) "$id:1" else null,
            isPinned = isPinned,
            authorChannelId = authorMid.takeIf { it > 0 }?.toString().orEmpty(),
            isHearted = isLikedByUploader,
        )

    private fun formatTime(seconds: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
            .format(Date(seconds * 1000))
}
