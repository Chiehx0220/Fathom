package io.github.aedev.flow.ui.screens.home.chips

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.SearchFilter
import io.github.aedev.flow.data.source.VideoSource
import io.github.aedev.flow.data.source.VideoSources
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeChipSourcesTest {
    private fun video(
        id: String,
        serviceId: Int,
    ) = Video(id, "t", "c", "ch-$id", "https://img/$id", 60, 1, uploadDate = "1 day ago", serviceId = serviceId)

    private fun context(filter: HomeContentSourceFilter) =
        ChipFeedContext(
            filters = { error("unused") },
            exclusions = { error("unused") },
            watched = { emptySet() },
            source = { filter },
        )

    private class Source(
        override val serviceId: Int,
        private val queryShape: (List<String>) -> List<String> = { it },
    ) : VideoSource {
        val asked = mutableListOf<Pair<String, SearchFilter>>()

        override suspend fun search(
            query: String,
            filter: SearchFilter,
        ): List<Video> {
            asked += query to filter
            return listOf(Video("$serviceId-$query", "t", "c", "ch", "", 60, 1, uploadDate = "1 day ago", serviceId = serviceId))
        }

        override suspend fun related(videoId: String): List<Video> = emptyList()

        override fun queriesFor(
            queries: List<String>,
            filter: SearchFilter,
        ): List<String> = queryShape(queries)
    }

    @Test
    fun `videos of a service the filter excludes drop out`() =
        runTest {
            val pool = listOf(video("y", 0), video("b", BILIBILI_SERVICE_ID))

            assertThat(context(HomeContentSourceFilter.BILIBILI).inSource(pool).map { it.id }).containsExactly("b")
            assertThat(context(HomeContentSourceFilter.YOUTUBE).inSource(pool).map { it.id }).containsExactly("y")
            assertThat(context(HomeContentSourceFilter.MIX).inSource(pool)).hasSize(2)
        }

    @Test
    fun `ids are told apart by their shape, not a saved service`() =
        runTest {
            val ids = listOf("dQw4w9WgXcQ", "BV1xx411c7mD")

            assertThat(context(HomeContentSourceFilter.BILIBILI).idsInSource(ids)).containsExactly("BV1xx411c7mD")
            assertThat(context(HomeContentSourceFilter.YOUTUBE).idsInSource(ids)).containsExactly("dQw4w9WgXcQ")
        }

    @Test
    fun `a search reaches only the allowed services, each with its own query shape`() =
        runTest {
            val youtube = Source(0)
            val bilibili = Source(BILIBILI_SERVICE_ID) { listOf(it.first()) }
            val sources = VideoSources(listOf(youtube, bilibili))

            val found = sources.searchAll(HomeContentSourceFilter.MIX, listOf("a", "b"), SearchFilter.LIVE)

            assertThat(found.map { it.id }).containsExactly("0-a", "0-b", "$BILIBILI_SERVICE_ID-a")
            assertThat(youtube.asked).containsExactly("a" to SearchFilter.LIVE, "b" to SearchFilter.LIVE)

            val only = Source(0)
            VideoSources(listOf(only, bilibili)).searchAll(HomeContentSourceFilter.BILIBILI, listOf("a", "b"), SearchFilter.ANY)
            assertThat(only.asked).isEmpty()
        }

    @Test
    fun `a mix-seed cover is high quality for YouTube and untouched elsewhere`() {
        assertThat(video("dQw4w9WgXcQ", 0).withHighQualityThumbnail().thumbnailUrl).contains("dQw4w9WgXcQ")
        assertThat(video("BV1xx411c7mD", BILIBILI_SERVICE_ID).withHighQualityThumbnail().thumbnailUrl).isEqualTo("https://img/BV1xx411c7mD")
    }
}
