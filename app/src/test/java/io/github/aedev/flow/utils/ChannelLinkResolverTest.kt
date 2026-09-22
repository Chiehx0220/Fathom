package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import org.junit.Test

class ChannelLinkResolverTest {
    @Test
    fun `a Bilibili space link from a NewPipe or PipePipe export resolves to the uploader id`() {
        val id = resolveNonYouTubeChannelId("https://space.bilibili.com/1551918022", BILIBILI_SERVICE_ID) { "" }

        assertThat(id).isEqualTo("1551918022")
    }

    @Test
    fun `an unreadable link falls back to the caller's value`() {
        assertThat(resolveNonYouTubeChannelId("https://example.com/x", BILIBILI_SERVICE_ID) { "fallback" }).isEqualTo("fallback")
        assertThat(resolveNonYouTubeChannelId("https://space.bilibili.com/1551918022", 6) { "fallback" }).isEqualTo("fallback")
    }
}
