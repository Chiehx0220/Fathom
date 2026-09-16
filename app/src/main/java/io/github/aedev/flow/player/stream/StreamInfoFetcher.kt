package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.data.model.isYouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Fetches NewPipe `StreamInfo` for playback, retrying transient extraction failures. */
object StreamInfoFetcher {
    private const val TAG = "StreamInfoFetcher"
    private const val ATTEMPTS = 3
    private const val TIMEOUT_MS = 12_000L

    /**
     * On YouTube, the second attempt swaps to the `youtu.be` short form: the two URL shapes take
     * different extractor paths, so one can succeed where the other fails on the same video. Other
     * services only have one URL shape (via [org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory]),
     * so every attempt reuses it.
     */
    suspend fun fetchForPlayback(
        videoId: String,
        serviceId: Int = ServiceList.YouTube.serviceId,
    ): StreamInfo? =
        withContext(Dispatchers.IO) {
            val service = NewPipe.getService(serviceId)
            val isYouTube = service.isYouTube
            var lastError: Throwable? = null
            repeat(ATTEMPTS) { attempt ->
                val info =
                    try {
                        val url =
                            if (isYouTube) {
                                if (attempt == 1) "https://youtu.be/$videoId" else "https://www.youtube.com/watch?v=$videoId"
                            } else {
                                service.streamLHFactory.getUrl(videoId)
                            }
                        withTimeoutOrNull(TIMEOUT_MS) {
                            StreamInfo.getInfo(service, url)
                        }
                    } catch (e: Exception) {
                        lastError = e
                        null
                    }
                if (info != null) return@withContext info
                if (attempt < 2) delay((attempt + 1) * 300L)
            }
            Log.e(TAG, "Failed to fetch stream info for $videoId", lastError)
            null
        }
}
