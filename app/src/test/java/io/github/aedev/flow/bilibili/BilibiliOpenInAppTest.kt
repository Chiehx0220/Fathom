package io.github.aedev.flow.bilibili

import android.app.Activity
import android.app.Application
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class)
class BilibiliOpenInAppTest {
    private val context = Robolectric.buildActivity(Activity::class.java).get()

    @Test
    fun `a video opens in the Bilibili app, not back in this app`() {
        openInBilibili(context, "BV1xx411c7mD")
        val intent = shadowOf(context).nextStartedActivity
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.`package`).isEqualTo("tv.danmaku.bili")
        assertThat(intent.data.toString()).contains("BV1xx411c7mD")
    }

    @Test
    fun `a live room opens its room page`() {
        openInBilibili(context, "live:12345")
        assertThat(shadowOf(context).nextStartedActivity.data.toString()).contains("live.bilibili.com/12345")
    }
}
