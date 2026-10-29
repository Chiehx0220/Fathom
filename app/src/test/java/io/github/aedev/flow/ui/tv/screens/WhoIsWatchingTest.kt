package io.github.aedev.flow.ui.tv.screens

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WhoIsWatchingTest {
    @Test
    fun `a cold start with two or more profiles asks`() {
        assertThat(asksWhoIsWatching(profileCount = 2, askEnabled = true, afterSwitch = false, hasLink = false)).isTrue()
    }

    @Test
    fun `one profile, the switch off, a switch just made or a link never asks`() {
        assertThat(asksWhoIsWatching(profileCount = 1, askEnabled = true, afterSwitch = false, hasLink = false)).isFalse()
        assertThat(asksWhoIsWatching(profileCount = 3, askEnabled = false, afterSwitch = false, hasLink = false)).isFalse()
        assertThat(asksWhoIsWatching(profileCount = 3, askEnabled = true, afterSwitch = true, hasLink = false)).isFalse()
        assertThat(asksWhoIsWatching(profileCount = 3, askEnabled = true, afterSwitch = false, hasLink = true)).isFalse()
    }
}
