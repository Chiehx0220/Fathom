package io.github.aedev.flow.player.error

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.bilibili.BilibiliLiveNotStartedException
import io.github.aedev.flow.bilibili.BilibiliLiveRebroadcastException
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class BilibiliVideoErrorsTest {
    private val context =
        mockk<Context> {
            every { getString(any()) } answers { "string:${firstArg<Int>()}" }
        }

    @Test
    fun `a room that has not started offers a retry`() {
        val error = VideoErrorMapper.from(context, BilibiliLiveNotStartedException("offline"))

        assertThat(error.message).isEqualTo("string:${R.string.error_live_not_started}")
        assertThat(error.hint).isEqualTo("string:${R.string.error_live_not_started_hint}")
        assertThat(error.isRetryable).isTrue()
        assertThat(error.isUserActionable).isFalse()
    }

    @Test
    fun `a rebroadcast room says so and does not offer a retry`() {
        val error = VideoErrorMapper.from(context, BilibiliLiveRebroadcastException("rebroadcast"))

        assertThat(error.message).isEqualTo("string:${R.string.error_live_rebroadcast}")
        assertThat(error.hint).isEqualTo("string:${R.string.error_live_rebroadcast_hint}")
        assertThat(error.isRetryable).isFalse()
        assertThat(error.isUserActionable).isFalse()
    }
}
