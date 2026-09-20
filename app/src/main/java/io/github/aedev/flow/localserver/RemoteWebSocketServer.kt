package org.schabi.newpipe.localserver

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer

import java.net.InetSocketAddress
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class RemoteWebSocketServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    private val connections: MutableSet<WebSocket> = Collections.newSetFromMap(ConcurrentHashMap())

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        connections.add(conn)
        LocalHttpServer.log("WebSocket client connected: " + conn.remoteSocketAddress)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        connections.remove(conn)
        LocalHttpServer.log("WebSocket client disconnected: " + conn.remoteSocketAddress)
    }

    override fun onMessage(conn: WebSocket, message: String?) {
        if (message != null && message.startsWith("register_client:")) {
            val clientName = message.substring("register_client:".length)
            conn.setAttachment(clientName)
            LocalHttpServer.log("Registered client: $clientName for IP " + conn.remoteSocketAddress)
            return
        }

        // Forward the message to LocalHttpServer pending commands queue
        LocalHttpServer.addPendingCommand(message)

        // Broadcast immediately to other connected clients (like the TV browser)
        broadcastCommand(message, conn)
    }

    override fun getConnections(): Set<WebSocket> = connections

    override fun onError(conn: WebSocket?, ex: Exception) {
        LocalHttpServer.log("WebSocket error: " + ex.message)
        if (conn != null) {
            connections.remove(conn)
        }
    }

    override fun onStart() {
        LocalHttpServer.log("WebSocket server started on port $port")
    }

    @JvmOverloads
    fun broadcastCommand(command: String?, excludeConn: WebSocket? = null) {
        for (conn in connections) {
            if (conn !== excludeConn && conn.isOpen) {
                try {
                    conn.send(command)
                } catch (e: Exception) {
                    LocalHttpServer.log("Failed to send WS command: " + e.message)
                }
            }
        }
    }
}
