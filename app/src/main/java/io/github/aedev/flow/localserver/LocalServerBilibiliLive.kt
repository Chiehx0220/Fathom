package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.bilibili.BilibiliContentNotAvailableException
import io.github.aedev.flow.bilibili.BilibiliLiveMessage
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.di.bilibiliApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/** Local server Bilibili live: room page as [StreamInfo], chat for web-player polling. */
internal object LocalServerBilibiliLive {
    private const val RECOMMENDED_LIMIT = 6
    private const val CHAT_KEPT = 200
    private const val CHAT_IDLE_MS = 30_000L
    private const val CHAT_SWEEP_MS = 10_000L

    /** Room number of a live room link; null if [mediaUrl] is not one. */
    fun roomIdOf(mediaUrl: String): Long? = BilibiliRef.Live.ofUrl(mediaUrl)?.roomId

    /**
     * Room metadata + HLS playlist. FLV-only room: not browser-playable → fails.
     */
    fun streamInfo(
        context: Context,
        roomId: Long,
    ): StreamInfo {
        val api = bilibiliApi(context)
        val playback = runBlocking { api.livePlayback(roomId) }
        val hls =
            playback.streams.hlsMasterUrl
                ?: throw BilibiliContentNotAvailableException("This room streams FLV only, which the web page cannot play")
        val room = playback.room
        val url = BilibiliRef.Live(room.roomId).url

        val info = StreamInfo(LocalServerBilibili.serviceId, url, url, StreamType.LIVE_STREAM, room.roomId.toString(), room.title, 0)
        info.thumbnails = listOf(Image(room.coverUrl, -1, -1, Image.ResolutionLevel.UNKNOWN))
        info.description = Description(room.tags.joinToString(" "), Description.PLAIN_TEXT)
        info.viewCount = room.viewerCount
        if (room.startedAtSec > 0) {
            info.uploadDate = DateWrapper(OffsetDateTime.ofInstant(Instant.ofEpochSecond(room.startedAtSec), ZoneOffset.ofHours(8)))
        }
        info.uploaderName = room.uploader.name
        info.uploaderUrl = LocalServerBilibili.channelUrl(room.uploader.mid.toString())
        info.uploaderAvatars = listOf(Image(room.uploader.avatarUrl, -1, -1, Image.ResolutionLevel.UNKNOWN))
        info.hlsUrl = hls
        info.relatedItems = runCatching { runBlocking { api.recommendedLives() } }.getOrDefault(emptyList()).map(::bilibiliLiveItem)
        return info
    }

    /** Rooms live now, list front. */
    fun recommended(context: Context): List<StreamInfoItem> =
        runCatching { runBlocking { bilibiliApi(context).recommendedLives() } }
            .getOrDefault(emptyList())
            .take(RECOMMENDED_LIMIT)
            .map(::bilibiliLiveItem)

    /** Room [mid] is live in now, as row; null if offline. */
    fun roomOf(
        context: Context,
        mid: Long,
    ): StreamInfoItem? = runCatching { runBlocking { bilibiliApi(context).channelLiveRoom(mid) } }.getOrNull()?.let(::bilibiliLiveItem)

    // region Chat

    private class Chat {
        private val lock = Any()
        private val items = ArrayDeque<Pair<Long, Bullet>>()
        private var seq = 0L

        @Volatile
        var lastPolledMs = System.currentTimeMillis()

        fun add(item: Bullet) =
            synchronized(lock) {
                items.addLast(++seq to item)
                if (items.size > CHAT_KEPT) items.removeFirst()
            }

        /** Everything after [since]; null [since] = first look, learns position only. */
        fun after(since: Long?): Pair<Long, List<Bullet>> =
            synchronized(lock) {
                seq to if (since == null) emptyList() else items.filter { it.first > since }.map { it.second }
            }
    }

    private val chats = ConcurrentHashMap<Long, Chat>()
    private val chatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Chat of [roomId] since [since]; socket opens on first poll, closes when idle. */
    fun chatSince(
        context: Context,
        roomId: Long,
        since: Long?,
    ): Pair<Long, List<Bullet>> {
        val chat = chats.computeIfAbsent(roomId) { open(context, roomId, Chat()) }
        chat.lastPolledMs = System.currentTimeMillis()
        return chat.after(since)
    }

    private fun open(
        context: Context,
        roomId: Long,
        chat: Chat,
    ): Chat {
        val api = bilibiliApi(context)
        chatScope.launch {
            launch { api.liveMessages(roomId).collect { chat.add(it.toBullet()) } }
            while (isActive) {
                delay(CHAT_SWEEP_MS)
                if (System.currentTimeMillis() - chat.lastPolledMs > CHAT_IDLE_MS) {
                    chats.remove(roomId)
                    cancel()
                }
            }
        }
        return chat
    }

    private fun BilibiliLiveMessage.toBullet(): Bullet =
        when (this) {
            is BilibiliLiveMessage.Chat -> Bullet(text, hex(argbColor), position.webName())
            is BilibiliLiveMessage.SuperChat -> Bullet(text, hex(argbColor), "top")
        }

    private fun hex(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

    // endregion
}
