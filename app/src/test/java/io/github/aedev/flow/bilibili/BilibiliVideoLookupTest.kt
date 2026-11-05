package io.github.aedev.flow.bilibili

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.fake.FakeBilibili
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class)
class BilibiliVideoLookupTest {
    private val bvid = "BV1GJ411x7h7"

    @Test
    fun `a bilibili id is named from the video's own page`() {
        val fake = FakeBilibili().apply { serve(FakeBilibili.VIEW, "view.json") }

        val video = runBlocking { BilibiliVideoLookup(fake.api()).video("$bvid?p=2") }

        assertThat(video?.id).isEqualTo("$bvid?p=2")
        assertThat(video?.title).isEqualTo("Sample video")
        assertThat(video?.channelName).isEqualTo("Uploader")
        assertThat(video?.serviceId).isEqualTo(BILIBILI_SERVICE_ID)
    }

    @Test
    fun `an id of another service is not looked up`() {
        val fake = FakeBilibili().apply { serve(FakeBilibili.VIEW, "view.json") }
        val lookup = BilibiliVideoLookup(fake.api())

        assertThat(runBlocking { lookup.video("dQw4w9WgXcQ") }).isNull()
        assertThat(runBlocking { lookup.video("live:123") }).isNull()
        assertThat(fake.count("")).isEqualTo(0)
    }

    @Test
    fun `a video bilibili will not describe stays unnamed`() {
        val fake = FakeBilibili().apply { serve(FakeBilibili.VIEW, "view_error.json") }

        assertThat(runBlocking { BilibiliVideoLookup(fake.api()).video(bvid) }).isNull()
    }
}
