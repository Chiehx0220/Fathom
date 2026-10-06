package io.github.aedev.flow.data.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliChannelVideo
import io.github.aedev.flow.bilibili.BilibiliChannelVideosPage
import io.github.aedev.flow.data.innertube.ChannelLabel
import io.github.aedev.flow.testing.DefaultLocaleRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class BilibiliSubscriptionFeedTest {
    @get:Rule
    val locale = DefaultLocaleRule()

    private val api: BilibiliApi = mockk()
    private val feed = BilibiliSubscriptionFeed(api)
    private val now = 1_700_000_000L

    private fun page(vararg bvidsWithAge: Pair<String, Long>) =
        BilibiliChannelVideosPage(
            videos =
                bvidsWithAge.map { (bvid, ageSec) ->
                    BilibiliChannelVideo(bvid, 1L, "title $bvid", "", 60, 10L, now - ageSec, "")
                },
            hasMore = false,
            lastAid = 0L,
        )

    private suspend fun readAll(
        ids: List<String>,
        labels: Map<String, ChannelLabel> = emptyMap(),
        minimumDateMillis: Long = 0L,
    ): List<BilibiliFeedChunk> {
        val chunks = mutableListOf<BilibiliFeedChunk>()
        feed.readInChunks(ids, labels, minimumDateMillis) { chunks += it }
        return chunks
    }

    @Test
    fun `an id that is not a number reads nothing and asks nobody`() =
        runTest {
            assertThat(feed.latestVideos("UCabc")).isEmpty()
        }

    @Test
    fun `uploaders are read three at a time and each group is handed over as soon as it is read`() =
        runTest {
            coEvery { api.channelVideos(any(), any()) } returns page("BV1" to 10L)

            val chunks = readAll(listOf("1", "2", "3", "4"))

            assertThat(chunks.map { it.videos.size }).containsExactly(3, 1).inOrder()
        }

    @Test
    fun `one uploader failing is reported with its reason and the others are still read`() =
        runTest {
            coEvery { api.channelVideos(1L, any()) } returns page("BV1" to 10L)
            coEvery { api.channelVideos(2L, any()) } throws IOException("risk control")

            val chunk = readAll(listOf("1", "2")).single()

            assertThat(chunk.videos.map { it.id }).containsExactly("BV1?p=1")
            assertThat(chunk.failureReasons.keys).containsExactly("2")
            assertThat(chunk.failureReasons.getValue("2")).isEqualTo("IOException: risk control")
        }

    @Test
    fun `uploads older than the cutoff are dropped`() =
        runTest {
            coEvery { api.channelVideos(1L, any()) } returns page("BVnew" to 10L, "BVold" to 100_000L)

            val chunk = readAll(listOf("1"), minimumDateMillis = (now - 1_000L) * 1000).single()

            assertThat(chunk.videos.map { it.id }).containsExactly("BVnew?p=1")
        }

    @Test
    fun `the subscribed name and avatar stand in for what the video list leaves out`() =
        runTest {
            coEvery { api.channelVideos(1L, any()) } returns page("BV1" to 10L)

            val video = feed.latestVideos("1", label = ChannelLabel("Uploader", "https://avatar")).single()

            assertThat(video.channelName).isEqualTo("Uploader")
            assertThat(video.channelThumbnailUrl).isEqualTo("https://avatar")
        }
}
