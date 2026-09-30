package io.github.aedev.flow.localserver

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

private const val MAX_HEAD_BYTES = 32 * 1024
private const val CRLF_CRLF = 0x0D0A0D0A
private const val LF_LF = 0x0A0A

/** Milliseconds a connection has to send its request line and headers. */
internal const val REQUEST_READ_TIMEOUT_MS = 15_000

internal const val CORS_PREFLIGHT =
    "HTTP/1.1 204 No Content\r\n" +
        "Access-Control-Allow-Origin: *\r\n" +
        "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
        "Access-Control-Allow-Headers: *\r\n" +
        "Access-Control-Expose-Headers: *\r\n" +
        "Access-Control-Max-Age: 86400\r\n" +
        "\r\n"

/**
 * The request line and header lines, read up to the blank line that ends them; null when the connection ends
 * or sends more than [MAX_HEAD_BYTES] first. Read as bytes, so nothing past the head is consumed as text.
 */
internal fun readRequestHead(socketInput: InputStream): List<String>? {
    val input = BufferedInputStream(socketInput)
    val bytes = ByteArrayOutputStream()
    var tail = 0
    while (bytes.size() < MAX_HEAD_BYTES) {
        val b = input.read()
        if (b < 0) return null
        bytes.write(b)
        tail = (tail shl 8) or b
        if (tail == CRLF_CRLF || (tail and 0xFFFF) == LF_LF) {
            val lines = String(bytes.toByteArray(), Charsets.UTF_8).split("\r\n", "\n").takeWhile { it.isNotEmpty() }
            return lines.takeIf { it.isNotEmpty() }
        }
    }
    return null
}

/** The header lines of [head] by lower-cased name. */
internal fun parseRequestHeaders(head: List<String>): MutableMap<String, String> {
    val headers = HashMap<String, String>()
    for (line in head.drop(1)) {
        val colon = line.indexOf(':')
        if (colon > 0) headers[line.substring(0, colon).trim().lowercase(Locale.US)] = line.substring(colon + 1).trim()
    }
    return headers
}
