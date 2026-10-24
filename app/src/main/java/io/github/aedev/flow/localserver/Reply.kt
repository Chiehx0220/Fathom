package io.github.aedev.flow.localserver

import org.json.JSONObject
import java.io.OutputStream

/** Response of one request. */
internal interface Reply {
    /** Text body. */
    fun send(
        code: Int,
        body: String,
        type: String,
        cacheControl: String? = null,
        setCookie: String? = null,
    )

    /** Fixed-length binary body. */
    fun sendBytes(
        code: Int,
        body: ByteArray,
        type: String,
        headers: Map<String, String> = emptyMap(),
    )

    /** Streamed body; [length] null = unknown. IOException from [write] = client gone. */
    fun stream(
        code: Int,
        type: String,
        length: Long?,
        headers: Map<String, String> = emptyMap(),
        write: (OutputStream) -> Unit,
    )
}

internal fun Reply.sendJson(body: String) = send(200, body, "application/json")

internal fun Reply.sendJson(body: JSONObject) = sendJson(body.toString())
