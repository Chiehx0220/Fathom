package io.github.aedev.flow.player.danmaku

import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliDanmaku
import io.github.aedev.flow.bilibili.BilibiliDanmakuPosition
import io.github.aedev.flow.bilibili.BilibiliVideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DanmakuPosition { SCROLL, TOP, BOTTOM }

data class DanmakuComment(
    val text: String,
    val timeMs: Long,
    val argbColor: Int,
    val position: DanmakuPosition,
    val relativeFontSize: Float,
)

/**
 * Loads Bilibili danmaku for the current video through the native client. Shaped after
 * [io.github.aedev.flow.player.sponsorblock.SponsorBlockHandler]: a StateFlow the UI collects, an
 * explicit reset() + load call per video rather than anything automatic.
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
     * Loads the danmaku of one Bilibili part through the native client, with no StreamInfo fetch
     * ahead of it: the part's cid is already in [info].
     */
    fun loadBilibili(
        api: BilibiliApi,
        info: BilibiliVideoInfo,
    ) {
        loadJob?.cancel()
        loadJob =
            scope.launch(Dispatchers.IO) {
                try {
                    val loaded = api.danmaku(info).map { it.toDanmakuComment() }
                    Log.w(TAG, "Bilibili danmaku for ${info.bvid}: ${loaded.size}")
                    withContext(Dispatchers.Main) { _comments.value = loaded }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load danmaku for ${info.bvid}: ${e.message}")
                }
            }
    }
}

private fun BilibiliDanmaku.toDanmakuComment() =
    DanmakuComment(
        text = text,
        timeMs = timeMs,
        argbColor = argbColor,
        position =
            when (position) {
                BilibiliDanmakuPosition.TOP -> DanmakuPosition.TOP
                BilibiliDanmakuPosition.BOTTOM -> DanmakuPosition.BOTTOM
                BilibiliDanmakuPosition.SCROLL -> DanmakuPosition.SCROLL
            },
        relativeFontSize = relativeFontSize,
    )
