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

/** YouTube streams: PoToken-attested multi-client InnerTube, not NewPipe's unattested client. Metadata stays on extractor. */
internal object LocalServerYouTubeStreams {
    private const val TAG = "LocalServerYouTube"
    private const val EXTRACTION_TTL_MS = 50 * 60 * 1000L
    private const val EXTRACTION_TIMEOUT_MS = 45_000L

    // Mint shares the player's single PoToken WebView: longer wait = queued behind in-app mint.
    private const val SLOW_EXTRACTION_WARNING_MS = 4000L

    private class CachedExtraction(
        val result: InnerTubeVideoStreamExtractor.VideoExtractionResult?,
        val expiresAtMs: Long,
    )

    private val extractionCache = ConcurrentHashMap<String, CachedExtraction>()
    private val extractionLock = Mutex()

    /** InnerTube extraction for [videoId], cached; null if all clients failed. */
    fun extractionFor(videoId: String): InnerTubeVideoStreamExtractor.VideoExtractionResult? {
        cached(videoId)?.let { return it.result }
        // Serialised: shared PoToken WebView + player parser lose replies under parallel calls.
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

    // MWEB/WEB direct URLs wall at ~60 s (rest SABR, unsupported); TV_TIZEN not walled
    val WEB_CLIENTS = setOf("MWEB", "WEB")
    private val walled: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Swap: refused URL reopened on a byte-identical file of a source that still serves it.
    private val swapTable = StreamSwapTable()
    private val swapper = RefusedStreamSwapper(swapTable, reportDenied = ::reportDenied, resolve = ::replacementsFor)

    /** Classifies refused [url]; demotes minting client as the player does; MWEB/WEB untouched. */
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

    /** URL serving [url]'s file once a swap is recorded; else [url]. */
    fun swapped(url: String): String = swapTable.rewrite(url) ?: url

    /** Replacement for refused [url] recorded; caller fetches [swapped]. */
    fun swapRefused(url: String): Boolean {
        if (swapTable.videoFor(url) == null) {
            reportDenied(url)
            return false
        }
        return swapper.onRefused(DataSpec(Uri.parse(url)), url)
    }

    /** Web-client URL for [videoId] refused: TV_TIZEN from now on. */
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

    /** Drops stale extraction: next request mints fresh URLs (after GVS denial). */
    fun invalidate(videoId: String) {
        extractionCache.remove(videoId)
    }

    /** Overlays [info] streams with InnerTube's, NewPipe's kept: InnerTube favors AV1/VP9; NewPipe H.264 = [StreamCatalog] fallback. */
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

/** [preferred] first, then [fallback] entries not present under the same itag. */
private fun <T> unionByItag(
    preferred: List<T>,
    fallback: List<T>,
    itagOf: (T) -> Int,
): List<T> {
    val seenItags = preferred.mapNotNullTo(HashSet()) { itagOf(it).takeIf { itag -> itag > 0 } }
    return preferred + fallback.filter { itagOf(it) <= 0 || seenItags.add(itagOf(it)) }
}

/** [base] streams ∪ InnerTube video-only/audio for [videoId], InnerTube first. */
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
