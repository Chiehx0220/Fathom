package io.github.aedev.flow.player.danmaku

import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliDanmaku
import io.github.aedev.flow.bilibili.BilibiliDanmakuPosition
import io.github.aedev.flow.bilibili.BilibiliLiveMessage
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

/** Bilibili danmaku of current video, shaped after [SponsorBlockHandler]: reset() + load per video. Live room: [liveRoomId]. */
class DanmakuHandler(
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "DanmakuHandler"
    }

    private val _comments = MutableStateFlow<List<DanmakuComment>>(emptyList())
    val comments: StateFlow<List<DanmakuComment>> = _comments.asStateFlow()

    private val _liveRoomId = MutableStateFlow<Long?>(null)
    val liveRoomId: StateFlow<Long?> = _liveRoomId.asStateFlow()

    private var loadJob: Job? = null

    fun reset() {
        loadJob?.cancel()
        _comments.value = emptyList()
        _liveRoomId.value = null
    }

    fun startLive(roomId: Long) {
        reset()
        _liveRoomId.value = roomId
    }

    /** Danmaku of one Bilibili part via native client, no StreamInfo fetch (part cid already in [info]). */
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

/** Live chat has no playback time: layer stamps arrival. */
fun BilibiliLiveMessage.toDanmakuComment(): DanmakuComment =
    when (this) {
        is BilibiliLiveMessage.Chat -> {
            DanmakuComment(
                text = text,
                timeMs = 0L,
                argbColor = argbColor,
                position =
                    when (position) {
                        BilibiliDanmakuPosition.TOP -> DanmakuPosition.TOP
                        BilibiliDanmakuPosition.BOTTOM -> DanmakuPosition.BOTTOM
                        BilibiliDanmakuPosition.SCROLL -> DanmakuPosition.SCROLL
                    },
                relativeFontSize = LIVE_FONT_SIZE,
            )
        }

        is BilibiliLiveMessage.SuperChat -> {
            DanmakuComment(text, 0L, argbColor, DanmakuPosition.TOP, LIVE_FONT_SIZE)
        }
    }

private const val LIVE_FONT_SIZE = 0.64f
