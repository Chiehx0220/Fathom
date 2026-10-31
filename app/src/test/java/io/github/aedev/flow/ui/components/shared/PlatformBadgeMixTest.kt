package io.github.aedev.flow.ui.components.shared

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.data.model.Video
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class PlatformBadgeMixTest {
    private fun video(
        id: String,
        serviceId: Int,
    ) = Video(
        id = id,
        title = id,
        channelName = "c",
        channelId = "c",
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0L,
        uploadDate = "",
        serviceId = serviceId,
    )

    @Test
    fun `a list of one service is not mixed`() {
        val youtube = ServiceList.YouTube.serviceId
        assertFalse(listOf(video("a", youtube), video("b", youtube)).mixesServices())
        assertFalse(listOf(video("a", BILIBILI_SERVICE_ID)).mixesServices())
        assertFalse(emptyList<Video>().mixesServices())
    }

    @Test
    fun `a list with two services is mixed`() {
        assertTrue(listOf(video("a", ServiceList.YouTube.serviceId), video("b", BILIBILI_SERVICE_ID)).mixesServices())
    }
}
