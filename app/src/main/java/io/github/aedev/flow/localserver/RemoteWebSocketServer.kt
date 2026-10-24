package io.github.aedev.flow.localserver

import org.java_websocket.WebSocket
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class RemoteWebSocketServer internal constructor(
    port: Int,
    private val pairing: Pairing,
) : WebSocketServer(InetSocketAddress(port)) {
    private val connections: MutableSet<WebSocket> = Collections.newSetFromMap(ConcurrentHashMap())

    init {
        // Restart soon after stop: bind fails while old sockets linger.
        isReuseAddr = true
    }

    override fun onOpen(
        conn: WebSocket,
        handshake: ClientHandshake,
    ) {
        if (!admits(conn, handshake)) {
            serverLog("WebSocket refused")
            conn.close(CloseFrame.POLICY_VALIDATION, OUTSIDE_NETWORK_REFUSAL)
            return
        }
        connections.add(conn)
        serverLog("WebSocket client connected: " + conn.remoteSocketAddress)
        RemoteSession.takeHeldCommands().forEach(conn::send)
    }

    private fun admits(
        conn: WebSocket,
        handshake: ClientHandshake,
    ): Boolean {
        val peer = conn.remoteSocketAddress?.address ?: return false
        if (!isLocalNetworkAddress(peer)) return false
        if (!isAllowedWebSocketOrigin(handshake.header("Origin"), handshake.header("Host"))) return false
        return peer.isLoopbackAddress || pairing.isPaired(handshake.header("Cookie"))
    }

    private fun ClientHandshake.header(name: String): String? = getFieldValue(name).ifEmpty { null }

    override fun onClose(
        conn: WebSocket,
        code: Int,
        reason: String,
        remote: Boolean,
    ) {
        connections.remove(conn)
        serverLog("WebSocket client disconnected: " + conn.remoteSocketAddress)
    }

    override fun onMessage(
        conn: WebSocket,
        message: String?,
    ) {
        if (message != null && message.startsWith("register_client:")) {
            val clientName = message.substring("register_client:".length)
            conn.setAttachment(clientName)
            serverLog("Registered client: $clientName for IP " + conn.remoteSocketAddress)
            return
        }

        // Forward to LocalHttpServer pending-commands queue
        RemoteSession.addPendingCommand(message)

        // Broadcast to other connected clients (e.g. TV browser)
        broadcastCommand(message, conn)
    }

    override fun getConnections(): Set<WebSocket> = connections

    override fun onError(
        conn: WebSocket?,
        ex: Exception,
    ) {
        // conn == null: server failed (typically port bind): nothing can connect.
        serverLog(if (conn == null) "WebSocket server failed on port $port: $ex" else "WebSocket error: " + ex.message)
        if (conn != null) {
            connections.remove(conn)
        }
    }

    override fun onStart() {
        serverLog("WebSocket server started on port $port")
    }

    fun closeAll() {
        for (conn in connections) conn.close(CloseFrame.NORMAL, TAKEN_OVER_REASON)
    }

    @JvmOverloads
    fun broadcastCommand(
        command: String?,
        excludeConn: WebSocket? = null,
    ) {
        for (conn in connections) {
            if (conn !== excludeConn && conn.isOpen) {
                try {
                    conn.send(command)
                } catch (e: Exception) {
                    serverLog("Failed to send WS command: " + e.message)
                }
            }
        }
    }
}

/** Close reason marking a takeover. */
internal const val TAKEN_OVER_REASON = "taken_over"
