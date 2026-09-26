package io.github.aedev.flow.data.comments

import io.github.aedev.flow.data.repository.YouTubeRepository

/** Where [CommentsPager] gets pages from; each service adapts its own API to this one shape. */
internal interface CommentsSource {
    suspend fun first(
        videoId: String,
        sortToken: String?,
    ): CommentsPageResult

    suspend fun more(
        videoId: String,
        continuation: String,
    ): CommentsPageResult

    suspend fun replies(
        videoId: String,
        commentId: String,
        continuation: String,
    ): CommentsPageResult
}

internal class YouTubeCommentsSource(
    private val repository: YouTubeRepository,
) : CommentsSource {
    override suspend fun first(
        videoId: String,
        sortToken: String?,
    ) = repository.getVideoComments(videoId, sortToken)

    override suspend fun more(
        videoId: String,
        continuation: String,
    ) = repository.getMoreVideoComments(videoId, continuation)

    override suspend fun replies(
        videoId: String,
        commentId: String,
        continuation: String,
    ) = repository.getVideoCommentReplies(videoId, continuation)
}
