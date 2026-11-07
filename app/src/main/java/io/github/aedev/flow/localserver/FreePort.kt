package io.github.aedev.flow.localserver

import java.net.InetSocketAddress
import java.net.ServerSocket

/** Ports tried in a row from [FIRST_PORT]. */
internal const val PORT_SPAN = 10

internal const val FIRST_PORT = 8080

/** Port and server of a successful [startOnFreePort]. */
internal data class Bound<T>(
    val port: Int,
    val value: T,
)

/** Probe before start: Ktor binds on a worker thread, a taken port is uncatchable there. */
internal fun canBind(port: Int): Boolean =
    runCatching {
        ServerSocket().use {
            it.reuseAddress = true
            it.bind(InetSocketAddress(port))
        }
    }.isSuccess

/** First port that [free] and [start] accept; null if none. */
internal fun <T> startOnFreePort(
    first: Int = FIRST_PORT,
    span: Int = PORT_SPAN,
    free: (Int) -> Boolean = ::canBind,
    start: (Int) -> T,
): Bound<T>? {
    for (port in first until first + span) {
        if (!free(port)) continue
        val value = runCatching { start(port) }.getOrNull() ?: continue
        return Bound(port, value)
    }
    return null
}
