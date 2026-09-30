package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.bilibili.BilibiliContentNotAvailableException
import io.github.aedev.flow.bilibili.BilibiliDanmakuPosition
import io.github.aedev.flow.bilibili.BilibiliLiveId
import io.github.aedev.flow.bilibili.BilibiliLiveMessage
import io.github.aedev.flow.di.bilibiliApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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

/** The local server's Bilibili live rooms: a room's page as a [StreamInfo], and its chat for the web player to poll. */
internal object LocalServerBilibiliLive {
    private const val RECOMMENDED_LIMIT = 6
    private const val CHAT_KEPT = 200
    private const val CHAT_IDLE_MS = 30_000L
    private const val CHAT_SWEEP_MS = 10_000L

    /** The room number in a live room link, or null when [mediaUrl] is not one. */
    fun roomIdOf(mediaUrl: String): Long? = BilibiliLiveId.fromUrl(mediaUrl)?.let(BilibiliLiveId::roomIdOf)

    /**
     * A room's metadata and HLS playlist, in the shape the watch page reads. A room that only publishes FLV
     * has nothing a browser can play, so it fails with a message rather than an empty player.
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
        val url = BilibiliLiveId.toUrl(BilibiliLiveId.of(room.roomId))

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

    /** Rooms live right now, for the front of a list. */
    fun recommended(context: Context): List<StreamInfoItem> =
        runCatching { runBlocking { bilibiliApi(context).recommendedLives() } }
            .getOrDefault(emptyList())
            .take(RECOMMENDED_LIMIT)
            .map(::bilibiliLiveItem)

    /** The room [mid] is live in right now, as a row; null when they are not live. */
    fun roomOf(
        context: Context,
        mid: Long,
    ): StreamInfoItem? =
        runCatching { runBlocking { bilibiliApi(context).channelLiveRoom(mid) } }.getOrNull()?.let(::bilibiliLiveItem)

    // region Chat

    private class Chat {
        private val lock = Any()
        private val items = ArrayDeque<Pair<Long, JSONObject>>()
        private var seq = 0L

        @Volatile
        var lastPolledMs = System.currentTimeMillis()

        fun add(item: JSONObject) =
            synchronized(lock) {
                items.addLast(++seq to item)
                if (items.size > CHAT_KEPT) items.removeFirst()
            }

        /** Everything after [since]; a null [since] is a first look, which only learns the position. */
        fun after(since: Long?): Pair<Long, JSONArray> =
            synchronized(lock) {
                val out = JSONArray()
                if (since != null) items.filter { it.first > since }.forEach { out.put(it.second) }
                seq to out
            }
    }

    private val chats = ConcurrentHashMap<Long, Chat>()
    private val chatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The chat of [roomId] since the position [since] the last poll returned. The room's socket opens on the
     * first poll and closes once nobody has polled for a while, so a closed tab does not hold a connection.
     */
    fun chatSince(
        context: Context,
        roomId: Long,
        since: Long?,
    ): Pair<Long, JSONArray> {
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
            launch { api.liveMessages(roomId).collect { chat.add(it.toJson()) } }
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

    private fun BilibiliLiveMessage.toJson(): JSONObject =
        when (this) {
            is BilibiliLiveMessage.Chat -> {
                JSONObject()
                    .put("text", text)
                    .put("color", hex(argbColor))
                    .put(
                        "position",
                        when (position) {
                            BilibiliDanmakuPosition.TOP -> "top"
                            BilibiliDanmakuPosition.BOTTOM -> "bottom"
                            BilibiliDanmakuPosition.SCROLL -> "scroll"
                        },
                    )
            }

            is BilibiliLiveMessage.SuperChat -> {
                JSONObject().put("text", text).put("color", hex(argbColor)).put("position", "top")
            }
        }

    private fun hex(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

    // endregion
}
