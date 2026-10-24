package io.github.aedev.flow.localserver

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** [Reply] on a Ktor call; blocks the handler thread. */
internal class KtorReply(
    private val call: ApplicationCall,
) : Reply {
    override fun send(
        code: Int,
        body: String,
        type: String,
        cacheControl: String?,
        setCookie: String?,
    ) = sendBytes(
        code,
        body.toByteArray(Charsets.UTF_8),
        type,
        buildMap {
            cacheControl?.let { put("Cache-Control", it) }
            setCookie?.let { put("Set-Cookie", it) }
        },
    )

    override fun sendBytes(
        code: Int,
        body: ByteArray,
        type: String,
        headers: Map<String, String>,
    ) {
        headers.forEach { (name, value) -> call.response.headers.append(name, value) }
        runBlocking { call.respondBytes(body, ContentType.parse(type), HttpStatusCode.fromValue(code)) }
    }

    override fun stream(
        code: Int,
        type: String,
        length: Long?,
        headers: Map<String, String>,
        write: (OutputStream) -> Unit,
    ) {
        val content = StreamContent(HttpStatusCode.fromValue(code), ContentType.parse(type), length, write)
        headers.forEach { (name, value) -> call.response.headers.append(name, value) }
        try {
            runBlocking { call.respond(content) }
        } catch (e: Exception) {
            // Head sent: client left mid-body.
            if (!call.response.isCommitted) throw e
            serverLog("Stream ended early: $e")
        }
    }
}

private class StreamContent(
    override val status: HttpStatusCode,
    override val contentType: ContentType,
    override val contentLength: Long?,
    private val write: (OutputStream) -> Unit,
) : OutgoingContent.WriteChannelContent() {
    override val headers: Headers = Headers.Empty

    override suspend fun writeTo(channel: ByteWriteChannel) {
        withContext(Dispatchers.IO) { channel.toOutputStream().use(write) }
    }
}
