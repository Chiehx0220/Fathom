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
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CoroutineDispatcher
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

private val CONTEXT = AttributeKey<RequestContext>("RequestContext")

/** Per request: network gate → header guard → pairing gate → [localRoutes]; blocking handlers on [executor]. */
internal fun Application.installLocalServer(
    dbHelper: HistoryDbHelper,
    executor: ExecutorService,
    pairing: Pairing,
    sockets: RemoteSockets,
    source: ContentSource,
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
        val context =
            RequestContext(
                InetAddress.getByName(call.request.local.remoteAddress),
                dbHelper,
                executor,
                pairing,
                source,
                call.request.path(),
                call.headerMap(),
            )
        call.attributes.put(CONTEXT, context)
        if (withContext(blocking) { admit(call, context) }) return@intercept
        finish()
    }
    routing {
        webSocket(REMOTE_PATH) { sockets.serve(this) }
        localRoutes(blocking)
    }
}

private fun ApplicationCall.headerMap() = request.headers.entries().associate { (name, values) -> name.lowercase() to values.first() }

private fun ApplicationCall.query() = Query(request.queryParameters.entries().associate { (name, values) -> name to values.last() })

/** True = continue to routing; false = answered here. */
private fun admit(
    call: ApplicationCall,
    context: RequestContext,
): Boolean {
    val reply = KtorReply(call)
    val request = call.request
    val method = request.httpMethod.value
    val path = request.path()
    val query = request.queryString()
    serverLog("Request: $method $path" + if (query.isNotEmpty()) "?$query" else "")

    if (!isLocalNetworkAddress(context.peer)) {
        serverLog("Refused a peer outside the local network")
        reply.send(403, OUTSIDE_NETWORK_REFUSAL, "text/plain; charset=UTF-8")
        return false
    }
    val headers = context.headers
    val refusal = refusalFor(method, path, headers)
    if (refusal != null) {
        serverLog("Refused $method $path: $refusal")
        reply.send(403, refusal, "text/plain; charset=UTF-8")
        return false
    }
    if (!context.admits(reply, path, headers)) return false
    if (path == REMOTE_PATH && !isAllowedWebSocketOrigin(headers["origin"], headers["host"])) {
        serverLog("Refused WebSocket from origin ${headers["origin"]}")
        reply.send(403, "Forbidden: this page is not the server's own", "text/plain; charset=UTF-8")
        return false
    }
    if (method == "OPTIONS" && path != REMOTE_PATH) {
        reply.sendBytes(204, ByteArray(0), "text/plain", PREFLIGHT_HEADERS)
        return false
    }
    return true
}

/** Routes of one server; handlers run on [blocking]. */
internal class RouteTable(
    private val root: Route,
    private val blocking: CoroutineDispatcher,
) {
    /** Own reply (text, bytes, stream); failures as text. */
    fun page(
        path: String,
        handler: Handler,
    ) = serve(path, json = false, handler)

    /** [handler] result as JSON; failures as JSON. */
    inline fun <reified T> api(
        path: String,
        crossinline handler: RequestContext.(Query) -> T,
    ) = serve(path, json = true) { reply, params -> reply.sendJson(handler(params)) }

    @PublishedApi
    internal fun serve(
        path: String,
        json: Boolean,
        handler: Handler,
    ) {
        root.route(path) {
            handle {
                val context = call.attributes[CONTEXT]
                withContext(blocking) { run(call, context, json, handler) }
            }
        }
    }
}

private fun run(
    call: ApplicationCall,
    context: RequestContext,
    json: Boolean,
    handler: Handler,
) {
    val reply = KtorReply(call)
    try {
        handler(context, reply, call.query())
    } catch (e: ApiFailure) {
        if (!call.response.isCommitted) reply.send(e.code, e.body, "application/json")
    } catch (e: BadRequestException) {
        failure(call, reply, json, 400, e)
    } catch (e: Exception) {
        serverLog("Error during route handling: $e")
        failure(call, reply, json, 500, e)
    }
}

private fun failure(
    call: ApplicationCall,
    reply: Reply,
    json: Boolean,
    code: Int,
    cause: Exception,
) {
    if (call.response.isCommitted) return
    val (body, type) = failureBody(json, code, cause.message)
    reply.send(code, body, type)
}
