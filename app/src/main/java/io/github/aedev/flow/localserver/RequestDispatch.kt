package io.github.aedev.flow.localserver

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.ExecutorService

private const val PING_PERIOD_MS = 30_000L

private val PREFLIGHT_HEADERS =
    mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers" to "*",
        "Access-Control-Expose-Headers" to "*",
        "Access-Control-Max-Age" to "86400",
    )

/** Per request: network gate → header guard → pairing gate → [ROUTES]; blocking handlers on [executor]. */
internal fun Application.installLocalServer(
    dbHelper: HistoryDbHelper,
    executor: ExecutorService,
    pairing: Pairing,
    sockets: RemoteSockets,
) {
    install(WebSockets) {
        pingPeriodMillis = PING_PERIOD_MS
        timeoutMillis = PING_PERIOD_MS
    }
    install(Compression) {
        gzip {
            minimumSize(512)
            matchContentType(ContentType.Text.Any, ContentType.Application.Json, ContentType.Application.JavaScript)
        }
    }
    val blocking = executor.asCoroutineDispatcher()
    intercept(ApplicationCallPipeline.Call) {
        val handler = RequestContext(InetAddress.getByName(call.request.local.remoteAddress), dbHelper, executor, pairing)
        if (!withContext(blocking) { serve(call, handler) }) return@intercept
        finish()
    }
    routing { webSocket(REMOTE_PATH) { sockets.serve(this) } }
}

/** False = passed to routing (WebSocket upgrade). */
private fun serve(
    call: ApplicationCall,
    handler: RequestContext,
): Boolean {
    val reply = KtorReply(call)
    val request = call.request
    val method = request.httpMethod.value
    val path = request.path()
    val query = request.queryString()
    serverLog("Request: $method $path" + if (query.isNotEmpty()) "?$query" else "")

    if (!isLocalNetworkAddress(handler.peer)) {
        serverLog("Refused a peer outside the local network")
        reply.send(403, OUTSIDE_NETWORK_REFUSAL, "text/plain; charset=UTF-8")
        return true
    }
    val headers = request.headers.entries().associate { (name, values) -> name.lowercase() to values.first() }
    val params = request.queryParameters.entries().associate { (name, values) -> name to values.last() }

    val refusal = refusalFor(method, path, headers)
    if (refusal != null) {
        serverLog("Refused $method $path: $refusal")
        reply.send(403, refusal, "text/plain; charset=UTF-8")
        return true
    }
    if (!handler.admits(reply, path, headers)) return true
    if (path == REMOTE_PATH) {
        if (isAllowedWebSocketOrigin(headers["origin"], headers["host"])) return false
        serverLog("Refused WebSocket from origin ${headers["origin"]}")
        reply.send(403, "Forbidden: this page is not the server's own", "text/plain; charset=UTF-8")
        return true
    }
    if (method == "OPTIONS") {
        reply.sendBytes(204, ByteArray(0), "text/plain", PREFLIGHT_HEADERS)
        return true
    }
    try {
        val route = ROUTES[path]
        if (route != null) {
            route(handler, reply, params, headers)
        } else if (path.startsWith("/vendor/")) {
            handler.handleVendorAsset(reply, path)
        } else {
            reply.send(404, "Page Not Found", "text/plain; charset=UTF-8")
        }
    } catch (e: BadRequestException) {
        failure(call, reply, path, 400, e)
    } catch (e: Exception) {
        serverLog("Error during route handling: $e")
        failure(call, reply, path, 500, e)
    }
    return true
}

private fun failure(
    call: ApplicationCall,
    reply: Reply,
    path: String,
    code: Int,
    cause: Exception,
) {
    if (call.response.isCommitted) return
    val (body, type) = failureBody(path, code, cause.message)
    reply.send(code, body, type)
}
