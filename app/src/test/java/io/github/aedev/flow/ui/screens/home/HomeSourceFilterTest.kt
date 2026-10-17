package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class HomeSourceFilterTest {
    private fun video(
        id: String,
        serviceId: Int,
    ) = Video(id, "t", "c", "ch-$id", "", 60, 1, uploadDate = "1 day ago", serviceId = serviceId)

    private val youtube = listOf(video("y1", 0), video("y2", 0))
    private val bilibili = listOf(video("b1", BILIBILI_SERVICE_ID), video("b2", BILIBILI_SERVICE_ID))

    @Test
    fun `a page keeps only the services the filter allows`() {
        assertThat((youtube + bilibili).forSourceFilter(HomeContentSourceFilter.BILIBILI)).containsExactlyElementsIn(bilibili)
        assertThat((youtube + bilibili).forSourceFilter(HomeContentSourceFilter.YOUTUBE)).containsExactlyElementsIn(youtube)
    }

    @Test
    fun `a mixed page spreads the services through it`() {
        val page = (youtube + bilibili).forSourceFilter(HomeContentSourceFilter.MIX)

        assertThat(page.map { it.id }).containsExactly("y1", "b1", "y2", "b2")
        assertThat(page.map { it.serviceId }.zipWithNext().count { (a, b) -> a != b }).isAtLeast(2)
    }
}
