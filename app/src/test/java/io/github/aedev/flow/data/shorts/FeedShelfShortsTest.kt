package io.github.aedev.flow.data.shorts

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf
import org.junit.Test

class FeedShelfShortsTest {
    private fun video(
        id: String,
        isShort: Boolean = false,
    ) = Video(
        id = id,
        title = id,
        channelName = "",
        channelId = "",
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0,
        uploadDate = "",
        isShort = isShort,
    )

    private val upload = FeedItem.VideoItem(video("upload"))
    private val reel = FeedItem.ShortItem(video("reel"))
    private val collabReel = FeedItem.VideoItem(video("collab", isShort = true))

    @Test
    fun `a Shorts shelf is dropped whole`() {
        val shelves = listOf(FeedShelf(id = "shorts", items = listOf(reel, reel.copy(video = video("reel2")))))

        assertThat(shelves.withoutShorts()).isEmpty()
    }

    @Test
    fun `a mixed shelf keeps only what is not a Short`() {
        val shelves = listOf(FeedShelf(id = "mixed", items = listOf(upload, reel, collabReel)))

        assertThat(shelves.withoutShorts().single().items).containsExactly(upload)
    }

    @Test
    fun `a collaboration reel in a video lockup counts as a Short`() {
        assertThat(collabReel.isShort()).isTrue()
        assertThat(upload.isShort()).isFalse()
    }

    @Test
    fun `shelves with no Shorts come back unchanged`() {
        val playlist = FeedItem.PlaylistItem(Playlist(id = "p", name = "p", thumbnailUrl = "", videoCount = 0))
        val shelves = listOf(FeedShelf(id = "videos", items = listOf(upload)), FeedShelf(id = "lists", items = listOf(playlist)))

        assertThat(shelves.withoutShorts()).isEqualTo(shelves)
    }

    @Test
    fun `a shelf that was already empty is left alone`() {
        val shelves = listOf(FeedShelf(id = "header"))

        assertThat(shelves.withoutShorts()).isEqualTo(shelves)
    }
}
