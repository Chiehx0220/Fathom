package io.github.aedev.flow.data.local.entity

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class VideoEntityServiceTest {
    private fun stub(id: String) =
        VideoEntity(
            id = id,
            title = "t",
            channelName = "c",
            channelId = "1",
            thumbnailUrl = "",
            duration = 0,
            viewCount = 0,
            uploadDate = "",
            description = "",
            channelThumbnailUrl = "",
        )

    @Test
    fun `synced Bilibili stub row resolves to Bilibili`() {
        assertThat(stub("BV1xx411c7mD?p=1").toDomain().serviceId).isEqualTo(BILIBILI_SERVICE_ID)
    }

    @Test
    fun `YouTube stub row stays YouTube`() {
        assertThat(stub("dQw4w9WgXcQ").toDomain().serviceId).isEqualTo(ServiceList.YouTube.serviceId)
    }
}
