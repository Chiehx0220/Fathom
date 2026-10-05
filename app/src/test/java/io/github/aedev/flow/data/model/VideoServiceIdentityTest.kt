package io.github.aedev.flow.data.model

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class VideoServiceIdentityTest {
    private val youTube = ServiceList.YouTube.serviceId

    private fun video(
        id: String,
        serviceId: Int,
    ) = Video(
        id = id,
        title = "t",
        channelName = "c",
        channelId = "1",
        thumbnailUrl = "",
        duration = 60,
        viewCount = 1,
        uploadDate = "",
        serviceId = serviceId,
    )

    @Test
    fun `a Bilibili id wins over a stored YouTube service id`() {
        val saved = video("BV1xx411c7mD", serviceId = youTube)

        assertThat(saved.resolvedServiceId).isEqualTo(BILIBILI_SERVICE_ID)
        assertThat(saved.isYouTube).isFalse()
    }

    @Test
    fun `a Bilibili live id wins over a stored YouTube service id`() {
        assertThat(video("live:12345", serviceId = youTube).isYouTube).isFalse()
        assertThat(video("BV1xx411c7mD?p=2", serviceId = youTube).resolvedServiceId).isEqualTo(BILIBILI_SERVICE_ID)
    }

    @Test
    fun `a YouTube id keeps the stored service id`() {
        assertThat(video("dQw4w9WgXcQ", serviceId = youTube).isYouTube).isTrue()
        assertThat(video("dQw4w9WgXcQ", serviceId = BILIBILI_SERVICE_ID).resolvedServiceId).isEqualTo(BILIBILI_SERVICE_ID)
    }

    @Test
    fun `a local media id has no shape of its own and keeps the stored service id`() {
        assertThat(video("local:42", serviceId = youTube).resolvedServiceId).isEqualTo(youTube)
    }
}
