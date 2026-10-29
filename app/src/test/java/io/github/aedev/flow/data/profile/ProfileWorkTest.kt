package io.github.aedev.flow.data.profile

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.notification.UpcomingReminder
import org.junit.Test

class ProfileWorkTest {
    private val default = RunningProfile(id = "d", isDefault = true)
    private val study = RunningProfile(id = "study", isDefault = false)

    @Test
    fun `work runs only in the profile that queued it`() {
        assertThat(ProfileWork.belongs("study", study)).isTrue()
        assertThat(ProfileWork.belongs("d", study)).isFalse()
        assertThat(ProfileWork.belongs("study", default)).isFalse()
    }

    @Test
    fun `work queued before profiles existed belongs to Default`() {
        assertThat(ProfileWork.belongs(null, default)).isTrue()
        assertThat(ProfileWork.belongs(null, study)).isFalse()
    }

    @Test
    fun `periodic work left by another profile is recognised by its tag`() {
        assertThat(ProfileWork.queuedByOther(setOf("profile:d", "SubscriptionCheckWorker"), study)).isTrue()
        assertThat(ProfileWork.queuedByOther(setOf("profile:study"), study)).isFalse()
        assertThat(ProfileWork.queuedByOther(setOf("SubscriptionCheckWorker"), default)).isFalse()
        assertThat(ProfileWork.queuedByOther(setOf("SubscriptionCheckWorker"), study)).isTrue()
    }

    @Test
    fun `a reminder is queued again until its premiere is six hours past`() {
        val now = 100_000_000L
        val future = UpcomingReminder("future", now + 60_000, "t", "c")
        val justStarted = UpcomingReminder("live", now - 60 * 60 * 1000L, "t", "c")
        val over = UpcomingReminder("over", now - 7 * 60 * 60 * 1000L, "t", "c")

        val (keep, drop) = ProfileWorkReconciler.remindersToRestore(listOf(future, justStarted, over), now)

        assertThat(keep).containsExactly(future, justStarted)
        assertThat(drop).containsExactly(over)
    }
}
