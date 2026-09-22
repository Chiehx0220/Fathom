package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.repository.SponsorBlockRepository
import kotlinx.coroutines.runBlocking

/**
 * Bridges to native `SponsorBlockRepository` instead of a separate HTTP client - inherits
 * AppProxyManager's proxy handling. Fetches 6/8 categories (no preview/filler); POI (single-
 * instant) segments are filtered out since the marker/skip-button UI expects real ranges.
 */
object SponsorBlockClient {
    data class Segment(
        val startMs: Long,
        val endMs: Long,
        // SponsorBlock's own category id (e.g. "sponsor", "selfpromo", "interaction") - matches
        // what HtmlRendererWatch's marker-color CSS classes and category labels key off of.
        val category: String,
    )

    /** Blocking - call from a background thread (every LocalHttpServer request already is one). */
    @JvmStatic
    fun fetchSegments(videoId: String): List<Segment> {
        if (videoId.isBlank()) return emptyList()
        return try {
            runBlocking {
                SponsorBlockRepository().getSegments(videoId)
                    .filter { it.actionType == "skip" }
                    .map { Segment((it.startTime * 1000).toLong(), (it.endTime * 1000).toLong(), it.category) }
            }
        } catch (e: Exception) {
            LocalHttpServer.log("SponsorBlock fetch failed for $videoId: ${e.message}")
            emptyList()
        }
    }
}
