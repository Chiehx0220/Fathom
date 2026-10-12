package io.github.aedev.flow.data.video.downloader.work

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.downloader.resolve.DownloadStreamResolver
import io.github.aedev.flow.data.video.downloader.resolve.ResolveOutcome
import io.github.aedev.flow.data.video.downloader.tags.DownloadKind
import io.github.aedev.flow.data.video.downloader.tags.DownloadTags
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DownloadTransferResolveTest {
    private val request = DownloadRequest(tags = DownloadTags(kind = DownloadKind.VIDEO, videoId = "v", title = "t"))
    private val resolver = mockk<DownloadStreamResolver>()
    private val transfer = DownloadTransfer(resolver, mockk(), mockk(), mockk())

    private fun fetch() = runBlocking { transfer.fetch(request, mockk(relaxed = true), itemId = 1, threads = 1) }

    @Test
    fun `an answer without a compatible audio track is resolved again before it is given up`() {
        coEvery { resolver.resolve(any(), any()) } returnsMany
            listOf(ResolveOutcome.NoCompatibleAudio, ResolveOutcome.NoCompatibleAudio, ResolveOutcome.NoCompatibleAudio)

        val outcome = fetch()

        assertThat((outcome as FetchOutcome.Failed).reason).isEqualTo(FetchFailure.NO_COMPATIBLE_AUDIO)
        coVerify(exactly = 3) { resolver.resolve(any(), any()) }
    }

    @Test
    fun `another outcome ends the retries`() {
        coEvery { resolver.resolve(any(), any()) } returnsMany listOf(ResolveOutcome.NoCompatibleAudio, ResolveOutcome.Unavailable)

        val outcome = fetch()

        assertThat((outcome as FetchOutcome.Failed).reason).isEqualTo(FetchFailure.UNAVAILABLE)
        coVerify(exactly = 2) { resolver.resolve(any(), any()) }
    }
}
