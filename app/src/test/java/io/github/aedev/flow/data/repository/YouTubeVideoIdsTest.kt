package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.needsCollaboratorResolution
import io.github.aedev.flow.player.stream.UpcomingPremiere
import io.github.aedev.flow.player.stream.UpcomingPremiereProbe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList

class YouTubeVideoIdsTest {
    private fun video(serviceId: Int) =
        Video(
            id = "v",
            title = "t",
            channelName = "A & B",
            channelId = "1",
            thumbnailUrl = "",
            duration = 60,
            viewCount = 1,
            uploadDate = "",
            serviceId = serviceId,
        )

    @Test
    fun `a YouTube id is sent to YouTube`() {
        assertThat("dQw4w9WgXcQ".isYouTubeVideoId).isTrue()
    }

    @Test
    fun `a Bilibili video or live id is never sent to YouTube`() {
        assertThat("BV1xx411c7mD".isYouTubeVideoId).isFalse()
        assertThat("BV1xx411c7mD?p=2".isYouTubeVideoId).isFalse()
        assertThat("live:12345".isYouTubeVideoId).isFalse()
    }

    @Test
    fun `only a YouTube video can need its collaborators looked up`() {
        assertThat(video(ServiceList.YouTube.serviceId).needsCollaboratorResolution()).isTrue()
        assertThat(video(BILIBILI_SERVICE_ID).needsCollaboratorResolution()).isFalse()
    }

    @Test
    fun `the premiere probe answers a Bilibili id without asking YouTube`() =
        runTest {
            assertThat(UpcomingPremiereProbe().probe("BV1xx411c7mD")).isEqualTo(UpcomingPremiere.NOT_UPCOMING)
        }
}
