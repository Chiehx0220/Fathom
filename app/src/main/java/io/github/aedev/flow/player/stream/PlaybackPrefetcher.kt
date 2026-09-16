package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.data.model.isYouTubeServiceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList

/**
 * Starts stream extraction the moment a video is tapped, ahead of the player screen composing and
 * the ViewModel running its load.
 *
 * This buys latency without buying network: the player's load path calls
 * [InnerTubeVideoStreamExtractor.extract] with the same arguments, so [InFlightRequestCoalescer]
 * makes that call join the request started here instead of opening a second client ladder. The
 * work simply began a few hundred milliseconds earlier — navigation, composition and the player
 * screen's own setup now overlap with the extraction rather than following it.
 */
object PlaybackPrefetcher {
    private const val TAG = "PlaybackPrefetcher"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var inFlight: Job? = null
    private var inFlightVideoId: String? = null

    /**
     * Warms extraction for [videoId]. Safe to call from the main thread; returns immediately.
     *
     * Local media plays from a `content://` URI and never touches extraction, so those ids are
     * ignored rather than sent through a client ladder that would certainly fail.
     *
     * [InnerTubeVideoStreamExtractor] only talks to YouTube's private API - for any other
     * [serviceId] (e.g. Bilibili) warming it would just fail after a full client-ladder timeout, so
     * it's skipped; that service's actual player load goes through the generic extractor instead.
     */
    fun prefetch(
        videoId: String,
        serviceId: Int = ServiceList.YouTube.serviceId,
    ) {
        if (videoId.isBlank() || videoId.startsWith("local_")) return
        if (!serviceId.isYouTubeServiceId) return
        synchronized(lock) {
            if (inFlightVideoId == videoId && inFlight?.isActive == true) return
            // Only the most recently tapped video is worth warming. Leaving an abandoned one
            // running would have it compete for bandwidth with the video actually being opened.
            inFlight?.cancel()
            inFlightVideoId = videoId
            inFlight =
                scope.launch {
                    runCatching { InnerTubeVideoStreamExtractor.extract(videoId) }
                        .onFailure { Log.d(TAG, "prefetch for $videoId ended: ${it.javaClass.simpleName}") }
                }
        }
    }
}
