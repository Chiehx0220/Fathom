package io.github.aedev.flow.bilibili

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.resolvedServiceId
import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Test

/** The service of a video or channel is worked out from its id, so a caller that builds one cannot forget to say. */
class DerivedServiceIdTest {
    private fun video(id: String) = Video(id, "t", "c", "ch", "", 60, 1, uploadDate = "1 day ago")

    private fun channel(id: String) = Channel(id, "n", "", 0L)

    @Test
    fun `a video takes its service from its id`() {
        assertThat(video("dQw4w9WgXcQ").serviceId).isEqualTo(0)
        assertThat(video("BV1xx411c7mD").serviceId).isEqualTo(BILIBILI_SERVICE_ID)
        assertThat(video("BV1xx411c7mD?p=2").serviceId).isEqualTo(BILIBILI_SERVICE_ID)
        assertThat(video("live:22908869").serviceId).isEqualTo(BILIBILI_SERVICE_ID)
    }

    @Test
    fun `a channel takes its service from its id`() {
        assertThat(channel("UCuAXFkgsw1L7xaCfnd5JJOw").serviceId).isEqualTo(0)
        assertThat(channel("@handle").serviceId).isEqualTo(0)
        assertThat(channel("123456").serviceId).isEqualTo(BILIBILI_SERVICE_ID)
    }

    @Test
    fun `a music track takes its service from its id`() {
        assertThat(MusicTrack("dQw4w9WgXcQ", "t", "a", "", 60).serviceId).isEqualTo(0)
        assertThat(MusicTrack("BV1xx411c7mD", "t", "a", "", 60).serviceId).isEqualTo(BILIBILI_SERVICE_ID)
    }

    @Test
    fun `a service passed explicitly is kept, and the id still wins over a wrong stored one`() {
        assertThat(video("dQw4w9WgXcQ").copy(serviceId = 7).serviceId).isEqualTo(7)
        assertThat(video("BV1xx411c7mD").copy(serviceId = 0).resolvedServiceId).isEqualTo(BILIBILI_SERVICE_ID)
    }

    @Test
    fun `a subscription keeps resolving a numeric channel id to Bilibili`() {
        assertThat(serviceIdOfChannel("123456", ChannelSubscription("123456", "n", "").serviceId)).isEqualTo(BILIBILI_SERVICE_ID)
    }
}
