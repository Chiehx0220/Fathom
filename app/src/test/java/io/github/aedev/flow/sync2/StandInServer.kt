package io.github.aedev.flow.sync2

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking

/** A local HTTP server that answers every request with [handle], standing in for a Flow sync server. */
class StandInServer(
    handle: suspend ApplicationCall.() -> Unit,
) : AutoCloseable {
    private val server =
        embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing { route("{...}") { handle { call.handle() } } }
        }.start(wait = false)

    val url: String = "http://127.0.0.1:${runBlocking {
        server.engine
            .resolvedConnectors()
            .first()
            .port
    }}/"

    override fun close() = server.stop(0, 0)
}

suspend fun ApplicationCall.respondJson(
    code: Int,
    body: String,
) = respondText(body, ContentType.Application.Json, HttpStatusCode.fromValue(code))
