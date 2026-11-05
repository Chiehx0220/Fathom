package io.github.aedev.flow.sync2.live

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BilibiliVideoLookup
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TrackDetailsTest {
    private val youtube = mockk<YouTubeRepository>()
    private val bilibili = mockk<BilibiliVideoLookup>()
    private val details = TrackDetails(youtube, bilibili)

    private fun video(id: String) =
        Video(
            id = id,
            title = "title of $id",
            channelName = "channel",
            channelId = "c",
            thumbnailUrl = "",
            duration = 60,
            viewCount = 1,
            uploadDate = "",
        )

    @Test
    fun `a bilibili video is named by bilibili and asked once`() {
        val id = "BV1GJ411x7h7?p=1"
        coEvery { bilibili.video(id) } returns video(id)

        assertThat(runBlocking { details.video(id) }?.title).isEqualTo("title of $id")
        assertThat(runBlocking { details.video(id) }?.title).isEqualTo("title of $id")

        coVerify(exactly = 1) { bilibili.video(id) }
        coVerify(exactly = 0) { youtube.getVideo(any()) }
    }

    @Test
    fun `any other id goes to youtube`() {
        coEvery { bilibili.video("dQw4w9WgXcQ") } returns null
        coEvery { youtube.getVideo("dQw4w9WgXcQ") } returns video("dQw4w9WgXcQ")

        assertThat(runBlocking { details.video("dQw4w9WgXcQ") }?.title).isEqualTo("title of dQw4w9WgXcQ")
    }
}
