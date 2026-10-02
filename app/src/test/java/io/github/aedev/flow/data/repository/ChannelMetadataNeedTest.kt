package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.data.model.Video
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class ChannelMetadataNeedTest {
    private fun video(
        channelId: String = "UCkVfrGwV-iG9bSsgCbrNPxQ",
        avatar: String = "https://yt3.ggpht.com/avatar=s176",
        serviceId: Int = ServiceList.YouTube.serviceId,
    ) = Video(
        id = "v",
        title = "t",
        channelName = "Better Stack",
        channelId = channelId,
        thumbnailUrl = "",
        duration = 60,
        viewCount = 1,
        uploadDate = "",
        channelThumbnailUrl = avatar,
        serviceId = serviceId,
    )

    @Test
    fun `a real channel with a real avatar needs nothing`() {
        assertThat(video().needsChannelMetadata()).isFalse()
    }

    @Test
    fun `a channel page saved as the avatar counts as missing`() {
        assertThat(video(avatar = "https://www.youtube.com/channel/UCkVfrGwV-iG9bSsgCbrNPxQ").needsChannelMetadata()).isTrue()
    }

    @Test
    fun `a blank avatar or a placeholder channel counts as missing`() {
        assertThat(video(avatar = "").needsChannelMetadata()).isTrue()
        assertThat(video(channelId = "local").needsChannelMetadata()).isTrue()
    }

    @Test
    fun `another service's video is never looked up on YouTube`() {
        assertThat(video(channelId = "12345", avatar = "", serviceId = BILIBILI_SERVICE_ID).needsChannelMetadata()).isFalse()
    }
}
