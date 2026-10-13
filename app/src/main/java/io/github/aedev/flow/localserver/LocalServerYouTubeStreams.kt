@file:OptIn(UnstableApi::class)

package io.github.aedev.flow.localserver

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.datasource.RefusedStreamSwapper
import io.github.aedev.flow.player.datasource.StreamSwapTable
import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.player.error.StreamDenialKind
import io.github.aedev.flow.player.stream.ClientGateTracker
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.TizenStreamResolver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.ConcurrentHashMap

/**
 * The local server's YouTube streams: InnerTube's PoToken-attested, multi-client extraction - the
 * same one the native player uses - instead of NewPipeExtractor's single unattested client. Only the
 * playable URLs move; metadata (title, related, comments, subtitles) stays on NewPipeExtractor.
 */
internal object LocalServerYouTubeStreams {
    private const val TAG = "LocalServerYouTube"
    private const val EXTRACTION_TTL_MS = 50 * 60 * 1000L
    private const val EXTRACTION_TIMEOUT_MS = 45_000L

    // Minting shares the native player's single PoToken WebView - a wait past this is more likely
    // queued behind an in-app playback mint than a slow network, worth telling apart in adb logcat.
    private const val SLOW_EXTRACTION_WARNING_MS = 4000L

    private class CachedExtraction(
        val result: InnerTubeVideoStreamExtractor.VideoExtractionResult?,
        val expiresAtMs: Long,
    )

    private val extractionCache = ConcurrentHashMap<String, CachedExtraction>()
    private val extractionLock = Mutex()

    /** InnerTube's extraction for [videoId], cached; null when every client failed. */
    fun extractionFor(videoId: String): InnerTubeVideoStreamExtractor.VideoExtractionResult? {
        cached(videoId)?.let { return it.result }
        // One extraction at a time: the PoToken WebView and NewPipe's player parser are shared, and parallel calls lose replies.
        return runBlocking { extractionLock.withLock { cached(videoId)?.result ?: extract(videoId) } }
    }

    private fun cached(videoId: String): CachedExtraction? =
        extractionCache[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }

    private suspend fun extract(videoId: String): InnerTubeVideoStreamExtractor.VideoExtractionResult? {
        val startedMs = System.currentTimeMillis()
        var finished = false
        val result =
            withTimeoutOrNull(EXTRACTION_TIMEOUT_MS) {
                walledToTizen(InnerTubeVideoStreamExtractor.extract(videoId), videoId).also { finished = true }
            }
        val elapsedMs = System.currentTimeMillis() - startedMs
        if (elapsedMs >= SLOW_EXTRACTION_WARNING_MS) {
            Log.w(TAG, "extract($videoId) took ${elapsedMs}ms - likely queued behind the shared PoToken WebView")
        }
        if (!finished) {
            Log.w(TAG, "extract($videoId) gave no answer in ${EXTRACTION_TIMEOUT_MS}ms")
            return null
        }
        result?.let { swapper.register(videoId, (it.videoFormats + it.audioFormats).map { format -> format.url }) }
        extractionCache[videoId] = CachedExtraction(result, System.currentTimeMillis() + EXTRACTION_TTL_MS)
        extractionCache.entries.removeIf { it.value.expiresAtMs < System.currentTimeMillis() }
        return result
    }

    // MWEB/WEB direct URLs wall at ~60 s (the rest is SABR, unsupported here); TV_TIZEN is not walled
    val WEB_CLIENTS = setOf("MWEB", "WEB")
    private val walled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // The player's own swap: a refused URL is reopened at the byte-identical file of a source that still serves it.
    private val swapTable = StreamSwapTable()
    private val swapper = RefusedStreamSwapper(swapTable, reportDenied = ::reportDenied, resolve = ::replacementsFor)

    /** Classifies a refused [url] and, like the player, demotes the client that minted it; MWEB/WEB are left alone here. */
    private fun reportDenied(url: String?): StreamDenialKind =
        if (StreamDenialClassifier.clientOf(url)?.uppercase() in WEB_CLIENTS) {
            StreamDenialClassifier.classify(url)
        } else {
            ClientGateTracker.reportDenied(url)
        }

    private suspend fun replacementsFor(
        videoId: String,
        kind: StreamDenialKind,
    ): List<PlayerResponse.StreamingData.Format>? {
        val result =
            when (kind) {
                StreamDenialKind.URL_EXPIRED -> InnerTubeVideoStreamExtractor.extract(videoId)
                else -> TizenStreamResolver.resolve(videoId)
            } ?: return null
        return result.videoFormats + result.audioFormats
    }

    /** The URL that serves [url]'s file once a swap is recorded for it, else [url]. */
    fun swapped(url: String): String = swapTable.rewrite(url) ?: url

    /** True when a replacement for the refused [url] is now recorded; the caller then fetches [swapped]. */
    fun swapRefused(url: String): Boolean {
        if (swapTable.videoFor(url) == null) {
            reportDenied(url)
            return false
        }
        return swapper.onRefused(DataSpec(Uri.parse(url)), url)
    }

    /** A web client's URL for [videoId] was refused: it is served from TV_TIZEN from now on. */
    fun markWalled(videoId: String) {
        if (walled.add(videoId)) invalidate(videoId)
    }

    private suspend fun walledToTizen(
        result: InnerTubeVideoStreamExtractor.VideoExtractionResult?,
        videoId: String,
    ): InnerTubeVideoStreamExtractor.VideoExtractionResult? {
        if (result == null || videoId !in walled || result.usedClient.clientName.uppercase() !in WEB_CLIENTS) return result
        val tizen = TizenStreamResolver.resolve(videoId)
        Log.d(TAG, "walled web streams for $videoId: TV_TIZEN ${if (tizen != null) "answered" else "unavailable"}")
        return tizen ?: result
    }

    /** Drops a stale extraction so the next request mints fresh URLs - after GVS denies one. */
    fun invalidate(videoId: String) {
        extractionCache.remove(videoId)
    }

    /**
     * Overlays [info]'s video-only/audio streams with InnerTube's, keeping NewPipe's as a union
     * rather than a replacement: InnerTube favors AV1/VP9, which some browsers cannot decode, and
     * NewPipe's H.264 gives [StreamCatalog]'s family picker a fallback.
     */
    fun overlay(
        info: StreamInfo,
        videoId: String,
    ) {
        val result = extractionFor(videoId) ?: return
        InnerTubeStreamBridge.convertVideoFormats(result.videoFormats).takeIf { it.isNotEmpty() }?.let {
            info.videoOnlyStreams = unionByItag(it, info.videoOnlyStreams.orEmpty()) { s -> s.itag }
        }
        InnerTubeStreamBridge.convertAudioFormats(result.audioFormats).takeIf { it.isNotEmpty() }?.let {
            info.audioStreams = unionByItag(it, info.audioStreams.orEmpty()) { s -> s.itag }
        }
    }
}

/** [preferred] first, then whichever of [fallback] isn't already there under the same itag. */
private fun <T> unionByItag(
    preferred: List<T>,
    fallback: List<T>,
    itagOf: (T) -> Int,
): List<T> {
    val seenItags = preferred.mapNotNullTo(HashSet()) { itagOf(it).takeIf { itag -> itag > 0 } }
    return preferred + fallback.filter { itagOf(it) <= 0 || seenItags.add(itagOf(it)) }
}

/** [base]'s streams unioned with InnerTube's video-only/audio streams for [videoId], InnerTube first. */
internal class MergedYouTubeStreams(
    private val base: StreamLists,
    private val videoId: String,
) : StreamLists {
    private val innerTube by lazy { LocalServerYouTubeStreams.extractionFor(videoId) }

    override val length: Long get() = base.length
    override val videoStreams: List<VideoStream> get() = base.videoStreams
    override val hlsUrl: String? get() = base.hlsUrl

    override val videoOnlyStreams: List<VideoStream> by lazy {
        val preferred = innerTube?.let { InnerTubeStreamBridge.convertVideoFormats(it.videoFormats) }.orEmpty()
        unionByItag(preferred, base.videoOnlyStreams) { it.itag }
    }

    override val audioStreams: List<AudioStream> by lazy {
        val preferred = innerTube?.let { InnerTubeStreamBridge.convertAudioFormats(it.audioFormats) }.orEmpty()
        unionByItag(preferred, base.audioStreams) { it.itag }
    }
}
