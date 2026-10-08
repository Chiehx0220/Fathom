package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Comment
import org.junit.Test

class YouTubeCommentItemTest {
    private fun comment(
        replyCount: Int = 0,
        continuation: String? = null,
        avatar: String = "https://yt3.ggpht.com/a",
    ) = Comment(
        id = "c1",
        author = "@someone",
        authorThumbnail = avatar,
        text = "hello",
        likeCount = 12,
        publishedTime = "2 days ago",
        replyCount = replyCount,
        isPinned = true,
        continuationToken = continuation,
        authorChannelId = "UCabc",
    )

    @Test
    fun theAvatarAndTheAuthorsChannelCarryOver() {
        val item = comment().toCommentItem("https://www.youtube.com/watch?v=x", hasReplies = true)
        assertThat(item.uploaderAvatarUrl).isEqualTo("https://yt3.ggpht.com/a")
        assertThat(item.uploaderUrl).isEqualTo("https://www.youtube.com/channel/UCabc")
        assertThat(item.uploaderName).isEqualTo("@someone")
        assertThat(item.likeCount).isEqualTo(12)
        assertThat(item.isPinned).isTrue()
    }

    @Test
    fun aBlankAvatarStaysEmptyInsteadOfAnEmptyImage() {
        val item = comment(avatar = "").toCommentItem("https://www.youtube.com/watch?v=x", hasReplies = true)
        assertThat(item.uploaderAvatarUrl).isNull()
    }

    @Test
    fun repliesOpenFromTheCommentsOwnContinuation() {
        val item = comment(replyCount = 3, continuation = "tok").toCommentItem("https://www.youtube.com/watch?v=x", hasReplies = true)
        assertThat(item.replies?.url).isEqualTo("yt:r:tok")
        assertThat(item.replyCount).isEqualTo(3)
    }

    @Test
    fun aReplyHasNoRepliesOfItsOwn() {
        val item = comment(replyCount = 3, continuation = "tok").toCommentItem("https://www.youtube.com/watch?v=x", hasReplies = false)
        assertThat(item.replies).isNull()
    }

    @Test
    fun onlyThisObjectsOwnContinuationsAreItsToAnswer() {
        assertThat(
            LocalServerYouTubeComments.owns(
                org.schabi.newpipe.extractor
                    .Page("yt:c:abc"),
            ),
        ).isTrue()
        assertThat(
            LocalServerYouTubeComments.owns(
                org.schabi.newpipe.extractor
                    .Page("https://www.youtube.com/youtubei"),
            ),
        ).isFalse()
        assertThat(LocalServerYouTubeComments.owns(null)).isFalse()
    }
}
