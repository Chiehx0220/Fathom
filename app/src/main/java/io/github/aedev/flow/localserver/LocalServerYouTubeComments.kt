package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.repository.YouTubeRepository
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description

/**
 * YouTube's comments through the app's own InnerTube client, the way the player screen reads them. The
 * extractor leaves out the avatars of YouTube's newer comment format, and InnerTube carries them.
 *
 * The continuation of a comment page is a [Page] whose url holds "yt:c:<token>", and the one of the replies
 * under a comment "yt:r:<token>". A page from the extractor has neither prefix, so it keeps going there.
 */
internal object LocalServerYouTubeComments {
    private const val MORE = "yt:c:"
    private const val REPLIES = "yt:r:"

    /** True for a continuation this object made, so [LocalServerSource] does not hand it to the extractor. */
    fun owns(page: Page?): Boolean = page?.url?.startsWith("yt:") == true

    /**
     * One page, or null when InnerTube gave nothing to show for a first page, so the extractor answers (it also knows
     * whether comments are turned off). A continuation that fails ends the list instead.
     */
    fun comments(
        context: Context,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult? {
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
                                page.continuation?.let {
                                    Page(REPLIES + it)
                                },
                            )
                        }
                    }

                    token.startsWith(MORE) -> {
                        repo.getMoreVideoComments(videoId, token.removePrefix(MORE)).let { page ->
                            CommentsResult(
                                page.comments.map { it.toCommentItem(videoUrl, hasReplies = true) },
                                page.continuation?.let {
                                    Page(MORE + it)
                                },
                            )
                        }
                    }

                    nextPage != null -> {
                        null
                    }

                    else -> {
                        val page = repo.getVideoComments(videoId)
                        // legacyPage is the repository's own fall back to the extractor: those comments have no avatars either.
                        if (page.legacyPage != null || page.comments.isEmpty()) {
                            null
                        } else {
                            CommentsResult(
                                page.comments.map { it.toCommentItem(videoUrl, hasReplies = true) },
                                page.continuation?.let {
                                    Page(MORE + it)
                                },
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            serverLog("InnerTube comments failed: ${e.message}")
            if (owns(nextPage)) CommentsResult(emptyList(), null) else null
        }
    }
}

/** A comment as the item type the local server's pages are built from; [hasReplies] is false for a reply. */
internal fun Comment.toCommentItem(
    videoUrl: String,
    hasReplies: Boolean,
): CommentsInfoItem {
    val comment = this
    return CommentsInfoItem(0, videoUrl, author).apply {
        setCommentId(comment.id)
        setCommentText(Description(comment.text, Description.PLAIN_TEXT))
        setUploaderName(comment.author)
        setUploaderUrl(if (comment.authorChannelId.isEmpty()) "" else channelIdToUrl(comment.authorChannelId))
        uploaderAvatarUrl = comment.authorThumbnail.takeIf { it.isNotBlank() }
        setLikeCount(comment.likeCount)
        setTextualUploadDate(comment.publishedTime)
        setPinned(comment.isPinned)
        setReplyCount(comment.replyCount)
        val replies = comment.continuationToken
        if (hasReplies && comment.replyCount > 0 && replies != null) setReplies(Page("yt:r:$replies"))
    }
}
