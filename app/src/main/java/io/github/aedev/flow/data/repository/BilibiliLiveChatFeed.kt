package io.github.aedev.flow.data.repository

import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliLiveMessage
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.data.model.LiveChatMessage
import io.github.aedev.flow.data.model.LiveChatMessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Push-to-poll adapter: room socket → bounded inbox drained by [poll]; reader stops [IDLE_MS] after the last poll. */
@Singleton
class BilibiliLiveChatFeed internal constructor(
    private val source: (roomId: Long) -> Flow<BilibiliLiveMessage>,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(api: BilibiliApi) : this(api::liveMessages, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private class Session(
        val room: Long,
        val inbox: Channel<LiveChatMessage>,
        val reader: Job,
    )

    private val lock = Any()
    private val serial = AtomicLong()
    private var session: Session? = null
    private var idle: Job? = null

    fun seed(videoId: String): String? =
        BilibiliRef.Live
            .ofId(videoId)
            ?.roomId
            ?.let { "$TOKEN_PREFIX$it" }

    /** Null when [continuation] is not a [seed] token. */
    fun poll(continuation: String): LiveChatRepository.LiveChatPage? {
        val room = continuation.takeIf { it.startsWith(TOKEN_PREFIX) }?.removePrefix(TOKEN_PREFIX)?.toLongOrNull() ?: return null
        val inbox =
            synchronized(lock) {
                val current = session?.takeIf { it.room == room && it.reader.isActive } ?: open(room)
                idle?.cancel()
                idle =
                    scope.launch {
                        delay(IDLE_MS)
                        close(current)
                    }
                current.inbox
            }
        val messages = buildList { while (true) add(inbox.tryReceive().getOrNull() ?: break) }
        return LiveChatRepository.LiveChatPage(messages, continuation, POLL_MS)
    }

    private fun open(room: Long): Session {
        session?.reader?.cancel()
        val inbox = Channel<LiveChatMessage>(MAX_BUFFERED, BufferOverflow.DROP_OLDEST)
        val reader = scope.launch { source(room).collect { inbox.trySend(it.toChatMessage("bilibili-$room-${serial.incrementAndGet()}")) } }
        return Session(room, inbox, reader).also { session = it }
    }

    private fun close(target: Session) =
        synchronized(lock) {
            if (session === target) {
                target.reader.cancel()
                session = null
            }
        }

    private fun BilibiliLiveMessage.toChatMessage(id: String): LiveChatMessage =
        when (this) {
            is BilibiliLiveMessage.Chat -> {
                LiveChatMessage(id = id, author = author, authorPhotoUrl = avatarUrl, message = text, timestamp = null)
            }

            is BilibiliLiveMessage.SuperChat -> {
                LiveChatMessage(
                    id = id,
                    author = author,
                    authorPhotoUrl = avatarUrl,
                    message = message,
                    timestamp = null,
                    type = LiveChatMessageType.SUPER_CHAT,
                    superChatAmount = "¥$priceYuan",
                    superChatArgb = argbColor,
                    superChatHeaderArgb = headerArgbColor,
                )
            }
        }

    private companion object {
        const val TOKEN_PREFIX = "bilibili-live:"
        const val POLL_MS = 1_000L
        const val IDLE_MS = 15_000L
        const val MAX_BUFFERED = 200
    }
}
