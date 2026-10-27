package io.github.aedev.flow.player

import android.content.Context
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliVideoInfo
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.player.danmaku.DanmakuComment
import io.github.aedev.flow.player.danmaku.DanmakuHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bilibili danmaku of [EnhancedPlayerManager]: manager forwards lifecycle only (attach, reset, playback loaded); rest lives here (few fork
 * lines in upstream file).
 */
class PlayerDanmaku {
    private var handler: DanmakuHandler? = null

    fun attach(scope: CoroutineScope) {
        handler = DanmakuHandler(scope)
    }

    fun reset() = handler?.reset()

    fun load(
        api: BilibiliApi,
        info: BilibiliVideoInfo,
    ) {
        handler?.reset()
        handler?.loadBilibili(api, info)
    }

    /** A video finished loading: its danmaku when it is a Bilibili one, otherwise clear the previous video's. */
    fun onPlaybackLoaded(
        info: BilibiliVideoInfo?,
        context: Context?,
    ) {
        if (info != null && context != null) load(bilibiliApi(context), info) else reset()
    }

    /** [roomId]: live room whose chat the danmaku layer shows. */
    fun startLive(roomId: Long) = handler?.startLive(roomId)

    val liveRoom: StateFlow<Long?>
        get() = handler?.liveRoomId ?: MutableStateFlow(null)

    val comments: StateFlow<List<DanmakuComment>>
        get() = handler?.comments ?: MutableStateFlow(emptyList())
}
