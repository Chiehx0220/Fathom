package io.github.aedev.flow.localserver

import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal const val REMOTE_PATH = "/remote"

/** Close reason marking a takeover. */
internal const val TAKEN_OVER_REASON = "taken_over"

/** Phone-remote sockets: pages receive commands, send none. */
internal class RemoteSockets(
    private val scope: CoroutineScope,
) {
    private val sessions = ConcurrentHashMap.newKeySet<DefaultWebSocketSession>()

    val count: Int get() = sessions.size

    suspend fun serve(session: DefaultWebSocketSession) {
        sessions.add(session)
        serverLog("WebSocket client connected")
        try {
            RemoteSession.takeHeldCommands().forEach { session.outgoing.send(Frame.Text(it)) }
            session.incoming.consumeEach { }
        } finally {
            sessions.remove(session)
            serverLog("WebSocket client disconnected")
        }
    }

    fun broadcast(command: String) {
        sessions.forEach { it.outgoing.trySend(Frame.Text(command)) }
    }

    fun closeAll() {
        sessions.forEach { session -> scope.launch { session.close(CloseReason(CloseReason.Codes.NORMAL, TAKEN_OVER_REASON)) } }
    }
}
