package io.github.aedev.flow.ui.components.shared

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.data.local.ThumbnailQuality
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/*
 * Bilibili cover not derivable from video id: a card saved without one (e.g. import lacking covers) asks Bilibili on display:
 * visible covers only, once each, never when a cover exists.
 */

/**
 * Card thumbnail candidates: [this] if any; else for Bilibili, the cover Bilibili returns (placeholder until then).
 */
@Composable
internal fun List<Any>.orBilibiliCover(
    videoId: String,
    quality: ThumbnailQuality?,
    portrait: Boolean,
): List<Any> {
    val context = LocalContext.current
    val bvid =
        if (isEmpty() && quality != null && BilibiliRef.ofVideoId(videoId) is BilibiliRef.Video) {
            BilibiliRef.Video.parse(videoId).bvid
        } else {
            null
        }
    val cover by produceState<String?>(null, bvid) { value = bvid?.let { BilibiliCoverCache.shared.coverOf(context, it) } }
    val resolved = cover
    if (bvid == null || resolved == null || quality == null) return this
    return ThumbnailUrlResolver.resolveVideoThumbnailCandidates(videoId, resolved, quality, portrait)
}

/**
 * Bilibili covers: one request per video (shared across cards), max [MAX_PARALLEL] concurrent, failures skipped for [RETRY_MS] (no per-recomposition retry).
 * Runs on [scope]: card scroll-away does not cancel another card's pending answer.
 */
internal class BilibiliCoverCache(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = TIMEOUT_MS,
    private val fetch: suspend (Context, String) -> String,
) {
    private val covers = ConcurrentHashMap<String, String>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()
    private val gate = Semaphore(MAX_PARALLEL)

    /** Cover of [bvid]; null if none available now. */
    suspend fun coverOf(
        context: Context,
        bvid: String,
    ): String? {
        covers[bvid]?.let { return it }
        failedAt[bvid]?.takeIf { now() - it < RETRY_MS }?.let { return null }
        return inFlight
            .computeIfAbsent(bvid) {
                scope.async(start = CoroutineStart.LAZY) { lookUp(context, bvid) }
            }.also { it.start() }
            .await()
    }

    private suspend fun lookUp(
        context: Context,
        bvid: String,
    ): String? {
        val cover =
            try {
                gate.withPermit { withTimeoutOrNull(timeoutMs) { fetch(context, bvid) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }?.takeIf { it.isNotBlank() }
        if (cover != null) covers[bvid] = cover else failedAt[bvid] = now()
        inFlight.remove(bvid)
        return cover
    }

    companion object {
        const val MAX_PARALLEL = 3
        const val TIMEOUT_MS = 8_000L
        const val RETRY_MS = 5 * 60_000L

        val shared = BilibiliCoverCache { context, bvid -> bilibiliApi(context).videoInfo(bvid).thumbnailUrl }
    }
}
