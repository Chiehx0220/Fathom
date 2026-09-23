package io.github.aedev.flow.localserver

import android.util.Log
import io.github.aedev.flow.player.stream.InnerTubeStreamBridge
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import kotlinx.coroutines.runBlocking
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

    // Minting shares the native player's single PoToken WebView - a wait past this is more likely
    // queued behind an in-app playback mint than a slow network, worth telling apart in adb logcat.
    private const val SLOW_EXTRACTION_WARNING_MS = 4000L

    private class CachedExtraction(
        val result: InnerTubeVideoStreamExtractor.VideoExtractionResult?,
        val expiresAtMs: Long,
    )

    private val extractionCache = ConcurrentHashMap<String, CachedExtraction>()

    /** InnerTube's extraction for [videoId], cached; null when every client failed. */
    fun extractionFor(videoId: String): InnerTubeVideoStreamExtractor.VideoExtractionResult? {
        extractionCache[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.result }
        val startedMs = System.currentTimeMillis()
        val result = runBlocking { InnerTubeVideoStreamExtractor.extract(videoId) }
        val elapsedMs = System.currentTimeMillis() - startedMs
        if (elapsedMs >= SLOW_EXTRACTION_WARNING_MS) {
            Log.w(TAG, "extract($videoId) took ${elapsedMs}ms - likely queued behind the shared PoToken WebView")
        }
        extractionCache[videoId] = CachedExtraction(result, System.currentTimeMillis() + EXTRACTION_TTL_MS)
        extractionCache.entries.removeIf { it.value.expiresAtMs < System.currentTimeMillis() }
        return result
    }

    /** Drops a stale extraction so the next request mints fresh URLs - after GVS denies one. */
    fun invalidate(videoId: String) {
        extractionCache.remove(videoId)
    }

    /** Overlays [info]'s video-only/audio streams with InnerTube's, keeping NewPipe's as a fallback. */
    fun overlay(
        info: StreamInfo,
        videoId: String,
    ) {
        val result = extractionFor(videoId) ?: return
        InnerTubeStreamBridge.convertVideoFormats(result.videoFormats).takeIf { it.isNotEmpty() }?.let { info.videoOnlyStreams = it }
        InnerTubeStreamBridge.convertAudioFormats(result.audioFormats).takeIf { it.isNotEmpty() }?.let { info.audioStreams = it }
    }
}

/** [base]'s streams, with InnerTube's video-only/audio streams for [videoId] wherever it has any. */
internal class MergedYouTubeStreams(
    private val base: StreamLists,
    private val videoId: String,
) : StreamLists {
    override val length: Long get() = base.length
    override val videoStreams: List<VideoStream> get() = base.videoStreams
    override val hlsUrl: String? get() = base.hlsUrl

    override val videoOnlyStreams: List<VideoStream>
        get() {
            val innerTube = innerTubeFormats()?.let { InnerTubeStreamBridge.convertVideoFormats(it.videoFormats) }
            return if (!innerTube.isNullOrEmpty()) innerTube else base.videoOnlyStreams
        }

    override val audioStreams: List<AudioStream>
        get() {
            val innerTube = innerTubeFormats()?.let { InnerTubeStreamBridge.convertAudioFormats(it.audioFormats) }
            return if (!innerTube.isNullOrEmpty()) innerTube else base.audioStreams
        }

    private fun innerTubeFormats() = LocalServerYouTubeStreams.extractionFor(videoId)
}
