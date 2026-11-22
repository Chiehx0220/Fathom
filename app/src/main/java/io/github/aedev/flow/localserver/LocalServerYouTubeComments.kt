package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.content.CommentsInfoItem
import io.github.aedev.flow.content.Description
import io.github.aedev.flow.content.Page
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.repository.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/** YouTube comments via app InnerTube. Continuation `yt:c:<token>`, replies `yt:r:<token>`. */
internal object LocalServerYouTubeComments {
    private const val MORE = "yt:c:"
    private const val REPLIES = "yt:r:"

    /** Continuation made by this object. */
    fun owns(page: Page?): Boolean = page?.url?.startsWith("yt:") == true

    /** One page; [CommentsResult.disabled] when off. Failed continuation ends list; failed first page throws. */
    fun comments(
        context: Context,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult {
        val videoId = LocalServerMedia.getVideoId(videoUrl)
        val token = nextPage?.url.orEmpty()
        val repo = YouTubeRepository.getInstance(PlayerPreferences(context))
        return try {
            runBlocking {
                when {
                    token.startsWith(REPLIES) -> {
                        repo.getVideoCommentReplies(videoId, token.removePrefix(REPLIES)).let { page ->
                            CommentsResult(
                                page.comments.map { it.toCommentItem(videoUrl, hasReplies = false) },
                                page.continuation?.let { Page(REPLIES + it) },
                            )
                        }
                    }

                    token.startsWith(MORE) -> {
                        repo.getMoreVideoComments(videoId, token.removePrefix(MORE)).let { page ->
                            CommentsResult(
                                page.comments.map { it.toCommentItem(videoUrl, hasReplies = true) },
                                page.continuation?.let { Page(MORE + it) },
                            )
                        }
                    }

                    // A cursor this object did not make: nothing to continue.
                    nextPage != null -> {
                        CommentsResult(emptyList(), null)
                    }

                    else -> {
                        val page = repo.getVideoComments(videoId)
                        CommentsResult(
                            page.comments.map { it.toCommentItem(videoUrl, hasReplies = true) },
                            page.continuation?.let { Page(MORE + it) },
                            disabled = page.disabled,
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            serverLog("InnerTube comments failed: ${e.message}")
            if (nextPage != null) CommentsResult(emptyList(), null) else throw e
        }
    }
}

/** Comment as local-server page item; [hasReplies] false for a reply. */
internal fun Comment.toCommentItem(
    videoUrl: String,
    hasReplies: Boolean,
): CommentsInfoItem {
    val comment = this
    return CommentsInfoItem(0, videoUrl, author).apply {
        commentId = comment.id
        commentText = Description(comment.text, Description.PLAIN_TEXT)
        uploaderName = comment.author
        uploaderUrl = if (comment.authorChannelId.isEmpty()) "" else channelIdToUrl(comment.authorChannelId)
        uploaderAvatarUrl = comment.authorThumbnail.takeIf { it.isNotBlank() }
        likeCount = comment.likeCount
        textualUploadDate = comment.publishedTime
        isPinned = comment.isPinned
        replyCount = comment.replyCount
        val replies = comment.continuationToken
        if (hasReplies && comment.replyCount > 0 && replies != null) this.replies = Page("yt:r:$replies")
    }
}
