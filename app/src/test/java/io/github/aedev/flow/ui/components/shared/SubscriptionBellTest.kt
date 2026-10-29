package io.github.aedev.flow.ui.components.shared

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.notifyingIds
import org.junit.Test

class SubscriptionBellTest {
    private fun channel(
        id: String,
        notify: Boolean,
    ) = ChannelSubscription(channelId = id, channelName = id, channelThumbnail = "", isNotificationEnabled = notify)

    @Test
    fun `only channels with the bell on are notifying`() {
        val ids = notifyingIds(listOf(channel("a", true), channel("b", false), channel("c", true)))

        assertThat(ids).containsExactly("a", "c")
    }

    @Test
    fun `alerts that can arrive block nothing`() {
        assertThat(bellAlertBlock(systemAllows = true, notificationsOn = true, newVideosOn = true)).isNull()
    }

    @Test
    fun `the system block wins over the app switches`() {
        assertThat(bellAlertBlock(systemAllows = false, notificationsOn = false, newVideosOn = false))
            .isEqualTo(BellAlertBlock.System)
    }

    @Test
    fun `either app switch off blocks the bell`() {
        assertThat(bellAlertBlock(systemAllows = true, notificationsOn = false, newVideosOn = true)).isEqualTo(BellAlertBlock.App)
        assertThat(bellAlertBlock(systemAllows = true, notificationsOn = true, newVideosOn = false)).isEqualTo(BellAlertBlock.App)
    }
}
