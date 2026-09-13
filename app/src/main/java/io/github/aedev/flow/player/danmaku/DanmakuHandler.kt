package io.github.aedev.flow.player.danmaku

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem

enum class DanmakuPosition { SCROLL, TOP, BOTTOM }

data class DanmakuComment(
    val text: String,
    val timeMs: Long,
    val argbColor: Int,
    val position: DanmakuPosition,
    val relativeFontSize: Float,
)

/**
 * Loads Bilibili danmaku ("bullet comments") for the current video - the native-player
 * counterpart of Local Server's own /danmaku route (LocalHttpServer.handleDanmaku), both backed by
 * the same PipePipeExtractor BulletCommentsExtractor. Shaped after
 * [io.github.aedev.flow.player.sponsorblock.SponsorBlockHandler]: a StateFlow the UI collects, an
 * explicit reset() + load call per video rather than anything automatic.
 *
 * Only Bilibili currently exposes a BulletCommentsExtractor
 * (StreamingService.getBulletCommentsExtractor() returns null for every other service), so
 * [loadComments] is a safe, cheap no-op for YouTube rather than something callers need to gate.
 */
class DanmakuHandler(
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "DanmakuHandler"
    }

    private val _comments = MutableStateFlow<List<DanmakuComment>>(emptyList())
    val comments: StateFlow<List<DanmakuComment>> = _comments.asStateFlow()

    private var loadJob: Job? = null

    fun reset() {
        loadJob?.cancel()
        _comments.value = emptyList()
    }

    /**
     * [videoUrl] must be the same URL the video's own StreamExtractor was just fetched with -
     * BilibiliBulletCommentsExtractor reads the video's cid out of a cache only that extractor's
     * own fetchPage() populates (keyed by video id), so this must run after stream extraction has
     * happened, not before. In practice that's always true here: a StreamInfo is required to start
     * playback at all, so the stream extractor has already run by the time a caller can reach this.
     */
    fun loadComments(
        serviceId: Int,
        videoUrl: String,
    ) {
        loadJob?.cancel()
        loadJob =
            scope.launch(Dispatchers.IO) {
                try {
                    val service = NewPipe.getService(serviceId)
                    val extractor = service.getBulletCommentsExtractor(videoUrl) ?: return@launch
                    extractor.fetchPage()
                    if (extractor.isLive) {
                        // Live danmaku needs a persistent WebSocket connection, which a one-shot
                        // load here has no equivalent of - out of scope, same as Local Server's own
                        // /danmaku route.
                        extractor.disconnect()
                        return@launch
                    }
                    val loaded = extractor.initialPage.items.map { it.toDanmakuComment() }
                    withContext(Dispatchers.Main) { _comments.value = loaded }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load danmaku for $videoUrl", e)
                }
            }
    }
}

// BulletCommentsInfoItem.getLastingTime() always returns -1 regardless of what's set (a bug in
// the extractor library itself, not this app's) - on-screen duration is hardcoded in the overlay
// that renders these instead of coming from here, matching Local Server's /danmaku route.
private fun BulletCommentsInfoItem.toDanmakuComment() =
    DanmakuComment(
        text = commentText ?: "",
        timeMs = duration?.toMillis() ?: 0L,
        argbColor = argbColor,
        position =
            when (position) {
                BulletCommentsInfoItem.Position.TOP -> DanmakuPosition.TOP
                BulletCommentsInfoItem.Position.BOTTOM -> DanmakuPosition.BOTTOM
                else -> DanmakuPosition.SCROLL
            },
        relativeFontSize = relativeFontSize.toFloat(),
    )
