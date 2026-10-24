package io.github.aedev.flow.localserver

import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Local web UI + API + remote-control WebSocket, one port. */
class LocalHttpServer(
    private val context: android.content.Context,
    private val port: Int,
) {
    init {
        WebAssets.init(context)
    }

    private var engine: EmbeddedServer<*, *>? = null
    private val threadPool: ExecutorService = Executors.newCachedThreadPool()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dbHelper: HistoryDbHelper = HistoryDbHelper.getInstance(context)
    private val pairing = Pairing.of(context)

    @Throws(IOException::class)
    fun startServer() {
        val sockets = RemoteSockets(scope)
        val server =
            embeddedServer(CIO, host = "0.0.0.0", port = port) {
                installLocalServer(dbHelper, threadPool, pairing, sockets)
            }
        server.start(wait = false)
        engine = server
        RemoteSession.commandBroadcaster = sockets::broadcast
        RemoteSession.listenerCount = sockets::count
        RemoteSession.listenerCloser = sockets::closeAll
        serverLog("Local server started on port $port")
    }

    fun stopServer() {
        engine?.stop(gracePeriodMillis = 0, timeoutMillis = 1000)
        engine = null
        RemoteSession.commandBroadcaster = null
        RemoteSession.listenerCount = { 0 }
        RemoteSession.listenerCloser = null
        scope.cancel()
        threadPool.shutdownNow()
        serverLog("Local server stopped.")
    }
}
