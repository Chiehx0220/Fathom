package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.repository.SponsorBlockRepository
import kotlinx.coroutines.runBlocking

/**
 * Bridge to native `SponsorBlockRepository` (inherits AppProxyManager proxy handling). 6/8 categories (no preview/filler);
 * POI (single-instant) segments dropped (marker/skip-button UI expects ranges).
 */
object SponsorBlockClient {
    data class Segment(
        val startMs: Long,
        val endMs: Long,
        // SponsorBlock category id ("sponsor", "selfpromo", "interaction"): key of web app marker-color CSS classes + labels.
        val category: String,
    )

    /** Blocking: background thread only (every LocalHttpServer request is one). */
    @JvmStatic
    fun fetchSegments(videoId: String): List<Segment> {
        if (videoId.isBlank()) return emptyList()
        return try {
            runBlocking {
                SponsorBlockRepository()
                    .getSegments(videoId)
                    .filter { it.actionType == "skip" }
                    .map { Segment((it.startTime * 1000).toLong(), (it.endTime * 1000).toLong(), it.category) }
            }
        } catch (e: Exception) {
            serverLog("SponsorBlock fetch failed for $videoId: ${e.message}")
            emptyList()
        }
    }
}
