package io.github.aedev.flow.bilibili

// Comment results; wire: CommentsResponse.kt.

data class BilibiliComment(
    val id: String,
    val authorMid: Long,
    val authorName: String,
    val authorAvatarUrl: String,
    val text: String,
    val likeCount: Long,
    val postedSec: Long,
    val replyCount: Int,
    val isPinned: Boolean,
    val isLikedByUploader: Boolean,
)

/** [nextOffset] null on last page. */
data class BilibiliCommentsPage(
    val comments: List<BilibiliComment>,
    val nextOffset: String?,
)

/** [nextPage] null on last page. */
data class BilibiliRepliesPage(
    val replies: List<BilibiliComment>,
    val nextPage: Int?,
)
