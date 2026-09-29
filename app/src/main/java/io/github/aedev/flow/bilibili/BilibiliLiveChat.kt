/*
 * Live chat over Bilibili's WebSocket, after PipePipeExtractor (GPL-3.0) BilibiliWebSocketClient.java.
 * Built on OkHttp's WebSocket and coroutines instead of a second WebSocket library: the heartbeat is a
 * coroutine that ends with the flow, and a dropped socket is reopened with a growing delay.
 */
package io.github.aedev.flow.bilibili

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

internal class BilibiliLiveChat(
    private val session: BilibiliSession,
    private val live: BilibiliLive,
    private val json: Json,
) {
    /** The room's chat until the collector stops, across dropped connections. */
    fun messages(roomId: Long): Flow<BilibiliLiveMessage> =
        flow {
            var failures = 0
            var attempt = 0
            while (currentCoroutineContext().isActive) {
                try {
                    val access = live.chatAccess(roomId)
                    // Each retry tries the next host, so one that is down or far does not hold the room's chat.
                    connect(access, access.endpoints[attempt++ % access.endpoints.size]).collect {
                        failures = 0
                        emit(it)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Live chat of room $roomId dropped: ${e.message}")
                }
                delay((RECONNECT_BASE_MS shl failures.coerceAtMost(MAX_BACKOFF_STEPS)).coerceAtMost(RECONNECT_MAX_MS))
                failures++
            }
        }

    /** One connection; ends when the socket closes or fails. */
    private fun connect(
        access: BilibiliLiveChatAccess,
        endpoint: String,
    ): Flow<BilibiliLiveMessage> =
        callbackFlow {
            var heartbeat: Job? = null
            val listener =
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        val auth = BilibiliLivePacket.authBody(access.roomId, access.token, access.buvid)
                        webSocket.send(BilibiliLivePacket.encode(BilibiliLivePacket.OP_AUTH, auth).toByteString())
                        val beat = BilibiliLivePacket.encode(BilibiliLivePacket.OP_HEARTBEAT).toByteString()
                        heartbeat =
                            launch {
                                while (isActive) {
                                    delay(HEARTBEAT_MS)
                                    webSocket.send(beat)
                                }
                            }
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        bytes: ByteString,
                    ) {
                        BilibiliLivePacket.messages(bytes.toByteArray(), json).forEach { trySend(it) }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        webSocket.close(NORMAL_CLOSURE, null)
                        close()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        close(t)
                    }
                }
            val headers = LinkedHashMap(session.userAgentHeaders(LIVE_HOME))
            headers["Origin"] = "https://live.bilibili.com"
            val socket = session.openWebSocket(endpoint, headers, listener)
            awaitClose {
                heartbeat?.cancel()
                socket.cancel()
            }
        }.buffer(BUFFERED_MESSAGES, BufferOverflow.DROP_OLDEST)

    private companion object {
        const val TAG = "BilibiliLiveChat"
        const val LIVE_HOME = "https://live.bilibili.com/"
        const val HEARTBEAT_MS = 30_000L
        const val RECONNECT_BASE_MS = 2_000L
        const val RECONNECT_MAX_MS = 30_000L
        const val MAX_BACKOFF_STEPS = 4
        const val NORMAL_CLOSURE = 1000
        const val BUFFERED_MESSAGES = 256
    }
}
