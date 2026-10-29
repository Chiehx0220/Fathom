package io.github.aedev.flow.notification

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationProfileTest {
    @Test
    fun `a notification opens only in the profile that posted it`() {
        assertThat(NotificationHelper.opensIn("study", runningId = "study")).isTrue()
        assertThat(NotificationHelper.opensIn("study", runningId = "default")).isFalse()
    }

    @Test
    fun `an intent without a profile, such as a download or a link, always opens`() {
        assertThat(NotificationHelper.opensIn(null, runningId = "study")).isTrue()
    }
}
