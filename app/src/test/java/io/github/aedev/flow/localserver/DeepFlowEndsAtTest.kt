package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.DEEP_FLOW_NEVER_EXPIRES_HOURS
import org.junit.Test

class DeepFlowEndsAtTest {
    @Test
    fun `a timer ends its hours after activation`() {
        assertThat(deepFlowEndsAt(activatedAt = 1_000L, expireHours = 4)).isEqualTo(1_000L + 4 * 3_600_000L)
    }

    @Test
    fun `no timer has no end`() {
        assertThat(deepFlowEndsAt(activatedAt = 1_000L, expireHours = DEEP_FLOW_NEVER_EXPIRES_HOURS)).isEqualTo(0L)
    }
}
