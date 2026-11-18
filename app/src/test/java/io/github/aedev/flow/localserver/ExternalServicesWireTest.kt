package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.contentfilter.aislist.AiVerdict
import org.junit.Test

class ExternalServicesWireTest {
    @Test
    fun `dislike counts are named and absent when unknown`() {
        assertThat(
            ApiJson.encodeToString(DislikesDto(true, dislikes = 12, likes = 34)),
        ).isEqualTo("""{"enabled":true,"dislikes":12,"likes":34}""")
        assertThat(ApiJson.encodeToString(DislikesDto(false))).isEqualTo("""{"enabled":false}""")
    }

    @Test
    fun `an AiSList answer carries the switches, the list size, the allowed channels and the verdicts`() {
        val dto =
            AiListDto(
                block = true,
                warn = false,
                channelCount = 1234,
                updatedAt = 99,
                allowed = listOf(AiAllowedDto("https://www.youtube.com/channel/UCa", "Allowed")),
                verdicts = mapOf("https://www.youtube.com/channel/UCb" to "warn"),
            )
        assertThat(ApiJson.encodeToString(dto)).isEqualTo(
            """{"block":true,"warn":false,"channelCount":1234,"updatedAt":99,"allowed":[{"id":"https://www.youtube.com/channel/UCa","name":"Allowed"}],"verdicts":{"https://www.youtube.com/channel/UCb":"warn"}}""",
        )
        assertThat(ApiJson.encodeToString(AiListDto(block = false, warn = false)))
            .isEqualTo("""{"block":false,"warn":false,"updatedAt":0,"allowed":[],"verdicts":{}}""")
    }

    @Test
    fun `only warn and block name a verdict`() {
        assertThat(AiVerdict.WARN.wireName()).isEqualTo("warn")
        assertThat(AiVerdict.BLOCK.wireName()).isEqualTo("block")
        assertThat(AiVerdict.NONE.wireName()).isNull()
    }
}
