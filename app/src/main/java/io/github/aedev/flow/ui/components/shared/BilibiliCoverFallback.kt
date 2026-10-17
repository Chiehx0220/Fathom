package io.github.aedev.flow.ui.components.shared

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.bilibili.BilibiliLiveId
import io.github.aedev.flow.bilibili.BilibiliVideoId
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
 * A Bilibili cover cannot be derived from the video id the way YouTube's can, so a Bilibili card that was
 * saved without one (an import whose export lacked covers) asks Bilibili for it when the card is shown:
 * only the covers on screen are ever asked for, each once, and a card that already has its cover never asks.
 */

/**
 * The thumbnail candidates of a card: [this] when it has any, otherwise, for a Bilibili video, those of the cover
 * Bilibili gives it once it has answered (the card shows its placeholder until then).
 */
@Composable
internal fun List<Any>.orBilibiliCover(
    videoId: String,
    quality: ThumbnailQuality?,
    portrait: Boolean,
): List<Any> {
    val context = LocalContext.current
    val bvid =
        if (isEmpty() && quality != null && !BilibiliLiveId.isLive(videoId) && BilibiliVideoId.isBilibili(videoId)) {
            BilibiliVideoId.parse(videoId).first
        } else {
            null
        }
    val cover by produceState<String?>(null, bvid) { value = bvid?.let { BilibiliCoverCache.shared.coverOf(context, it) } }
    val resolved = cover
    if (bvid == null || resolved == null || quality == null) return this
    return ThumbnailUrlResolver.resolveVideoThumbnailCandidates(videoId, resolved, quality, portrait)
}

/**
 * Covers asked of Bilibili: one request per video however many cards want it, at most [MAX_PARALLEL] at once,
 * and a video that could not be answered is left alone for [RETRY_MS] instead of being asked again by every recomposition.
 * The requests run on [scope], so a card scrolling away does not cancel the answer the next card is waiting for.
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

    /** The cover of [bvid], or null when Bilibili has none to give right now. */
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
