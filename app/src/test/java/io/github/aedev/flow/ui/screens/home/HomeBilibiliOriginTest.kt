package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class HomeBilibiliOriginTest {
    private fun video(id: String) = Video(id, "Title $id", "Channel", "ch-$id", "", 600, 1_000, uploadDate = "1 day ago")

    private val discovery = { _: String -> FeedSource.DISCOVERY }

    @Test
    fun `a Bilibili card a search found names that search`() {
        val origins =
            pageOriginsWithBilibili(listOf(video("BV1xx411c7mD")), discovery, emptyMap(), mapOf("BV1xx411c7mD" to "老司机"), emptySet())

        assertThat(origins).containsExactly("BV1xx411c7mD", HomeCardOrigin.search("老司机"))
    }

    @Test
    fun `a Bilibili trending or live card no search found gets no search label`() {
        val origins =
            pageOriginsWithBilibili(listOf(video("BV1xx411c7mD"), video("live:22908869")), discovery, emptyMap(), emptyMap(), emptySet())

        assertThat(origins).isEmpty()
    }

    @Test
    fun `a YouTube card whose search is no longer known still says it came from a search`() {
        val origins = pageOriginsWithBilibili(listOf(video("dQw4w9WgXcQ")), discovery, emptyMap(), emptyMap(), emptySet())

        assertThat(origins).containsExactly("dQw4w9WgXcQ", HomeCardOrigin.search(null))
    }

    @Test
    fun `a Bilibili card in the related lane keeps its seed`() {
        val origins =
            pageOriginsWithBilibili(
                listOf(video("BV1xx411c7mD")),
                { FeedSource.RELATED },
                mapOf("BV1xx411c7mD" to "上一集"),
                emptyMap(),
                emptySet(),
            )

        assertThat(origins).containsExactly("BV1xx411c7mD", HomeCardOrigin.related("上一集"))
    }
}
