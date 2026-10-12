package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class CipherSignatureFallbackTest {
    private val format =
        PlayerResponse.StreamingData.Format(
            itag = 137,
            url = null,
            mimeType = "video/mp4",
            bitrate = 1,
            width = 1920,
            height = 1080,
            contentLength = null,
            quality = "hd1080",
            fps = 24,
            qualityLabel = null,
            averageBitrate = null,
            audioQuality = null,
            approxDurationMs = null,
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = "s=x&sp=sig&url=u",
        )
    private var clock = 1_790_000_000_000L
    private var newPipeCalls = 0
    private var playerCalls = 0

    @Before
    fun setUp() = CipherSignatureFallback.reset()

    @After
    fun tearDown() = CipherSignatureFallback.reset()

    private fun resolve(
        newPipe: String?,
        player: String?,
    ) = runBlocking {
        CipherSignatureFallback.streamUrl(
            format,
            viaNewPipe = {
                newPipeCalls++
                newPipe
            },
            viaPlayer = {
                playerCalls++
                player
            },
            nowMs = { clock },
        )
    }

    @Test
    fun `NewPipe's answer is used and the player function is not asked`() {
        assertThat(resolve("newpipe", "player")).isEqualTo("newpipe")
        assertThat(playerCalls).isEqualTo(0)
    }

    @Test
    fun `the player function answers when NewPipe fails, and NewPipe is then skipped`() {
        assertThat(resolve(null, "player")).isEqualTo("player")
        assertThat(resolve("newpipe", "player")).isEqualTo("player")
        assertThat(newPipeCalls).isEqualTo(1)
    }

    @Test
    fun `NewPipe is asked again after the skip window`() {
        resolve(null, "player")
        clock += 31 * 60 * 1000L
        assertThat(resolve("newpipe", "player")).isEqualTo("newpipe")
        assertThat(newPipeCalls).isEqualTo(2)
    }

    @Test
    fun `a failure of both is not remembered against NewPipe`() {
        assertThat(resolve(null, null)).isNull()
        assertThat(resolve("newpipe", null)).isEqualTo("newpipe")
        assertThat(newPipeCalls).isEqualTo(2)
    }
}
