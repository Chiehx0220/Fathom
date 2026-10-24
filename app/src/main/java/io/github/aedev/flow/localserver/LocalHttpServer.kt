package io.github.aedev.flow.localserver

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Local web UI + API + remote-control WebSocket. */
class LocalHttpServer(
    private val context: android.content.Context,
    private val port: Int,
) {
    init {
        WebAssets.init(context)
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val threadPool: ExecutorService = Executors.newCachedThreadPool()
    private val dbHelper: HistoryDbHelper = HistoryDbHelper.getInstance(context)
    private val pairing = Pairing.of(context)

    @Throws(IOException::class)
    fun startServer() {
        val serverSocket = ServerSocket(port)
        this.serverSocket = serverSocket
        isRunning = true
        serverLog("Local server started on port $port")

        if (wsServer == null) {
            val server = RemoteWebSocketServer(8081, pairing)
            wsServer = server
            server.start()
            RemoteSession.commandBroadcaster = server::broadcastCommand
            RemoteSession.listenerCount = { server.connections.size }
            RemoteSession.listenerCloser = server::closeAll
        }

        threadPool.execute {
            while (isRunning) {
                try {
                    val socket = serverSocket.accept()
                    if (!isLocalNetworkAddress(socket.inetAddress)) {
                        serverLog("Refused a peer outside the local network")
                        socket.refuseOutsideNetwork()
                        continue
                    }
                    threadPool.execute(ClientHandler(socket, dbHelper, context, threadPool, pairing))
                } catch (e: IOException) {
                    if (!isRunning) break
                    serverLog("Socket accept error: " + e.message)
                }
            }
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
        }
        val server = wsServer
        if (server != null) {
            try {
                server.stop()
            } catch (e: InterruptedException) {
            }
            wsServer = null
            RemoteSession.commandBroadcaster = null
            RemoteSession.listenerCount = { 0 }
            RemoteSession.listenerCloser = null
        }
        threadPool.shutdownNow()
        serverLog("Local server stopped.")
    }

    private companion object {
        @Volatile
        private var wsServer: RemoteWebSocketServer? = null
    }
}
