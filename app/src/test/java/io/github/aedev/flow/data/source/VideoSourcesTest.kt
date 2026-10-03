package io.github.aedev.flow.data.source

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.data.model.Video
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class VideoSourcesTest {
    private class FakeSource(
        override val serviceId: Int,
    ) : VideoSource {
        override suspend fun search(
            query: String,
            filter: SearchFilter,
        ): List<Video> = emptyList()

        override suspend fun related(videoId: String): List<Video> = emptyList()
    }

    private val youtube = FakeSource(ServiceList.YouTube.serviceId)
    private val bilibili = FakeSource(BILIBILI_SERVICE_ID)
    private val sources = VideoSources(listOf(youtube, bilibili))

    @Test
    fun `a filter asks only the services it allows`() {
        assertThat(sources.forFilter(HomeContentSourceFilter.MIX)).containsExactly(youtube, bilibili)
        assertThat(sources.forFilter(HomeContentSourceFilter.YOUTUBE)).containsExactly(youtube)
        assertThat(sources.forFilter(HomeContentSourceFilter.BILIBILI)).containsExactly(bilibili)
    }

    @Test
    fun `a video goes to the service its id belongs to`() {
        assertThat(sources.forVideo("dQw4w9WgXcQ")).isSameInstanceAs(youtube)
        assertThat(sources.forVideo("BV1xx411c7mD?p=2")).isSameInstanceAs(bilibili)
        assertThat(sources.forVideo("live:123")).isSameInstanceAs(bilibili)
    }

    @Test
    fun `a wrong saved service id loses to the shape of the id`() {
        assertThat(sources.forVideo("BV1xx411c7mD", savedServiceId = ServiceList.YouTube.serviceId)).isSameInstanceAs(bilibili)
    }

    @Test
    fun `Bilibili takes one query for live and at most two otherwise`() {
        val bilibiliSource = BilibiliVideoSource(mockk())
        val queries = listOf("a", "b", "c")
        assertThat(bilibiliSource.queriesFor(queries, SearchFilter.LIVE)).containsExactly("a")
        assertThat(bilibiliSource.queriesFor(queries, SearchFilter.ANY)).containsExactly("a", "b").inOrder()
        assertThat(youtube.queriesFor(queries, SearchFilter.ANY)).containsExactlyElementsIn(queries).inOrder()
    }

    @Test
    fun `a failing fetch yields no videos`() =
        runTest {
            val result = boundedOrEmpty<Video>(1_000L) { error("boom") }
            assertThat(result).isEmpty()
        }

    @Test
    fun `a fetch that runs out of time yields no videos`() =
        runTest {
            val result = boundedOrEmpty<Video>(10L) { delay(1_000L).let { listOf() } }
            assertThat(result).isEmpty()
        }

    @Test
    fun `cancellation is not swallowed`() {
        assertThrows(CancellationException::class.java) {
            runBlocking { boundedOrEmpty<Video>(1_000L) { throw CancellationException("stop") } }
        }
    }
}
