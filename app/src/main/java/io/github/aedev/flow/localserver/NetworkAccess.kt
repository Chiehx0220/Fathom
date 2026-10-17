package io.github.aedev.flow.localserver

import java.net.InetAddress
import java.net.Socket

internal const val OUTSIDE_NETWORK_REFUSAL = "Forbidden: this server only answers devices on the same local network"

/** Loopback, private, link-local, unique-local, or the CGNAT range overlay VPNs use. */
internal fun isLocalNetworkAddress(address: InetAddress): Boolean {
    if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress) return true
    val bytes = address.address
    return when (bytes.size) {
        4 -> (bytes[0].toInt() and 0xFF) == 100 && (bytes[1].toInt() and 0xC0) == 64
        16 -> (bytes[0].toInt() and 0xFE) == 0xFC
        else -> false
    }
}

/** 403 for a peer outside the local network, written without reading its request. */
internal fun outsideNetworkResponse(): ByteArray {
    val body = OUTSIDE_NETWORK_REFUSAL.toByteArray(Charsets.UTF_8)
    val head =
        "HTTP/1.1 403 Forbidden\r\nContent-Type: text/plain; charset=UTF-8\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
    return head.toByteArray(Charsets.UTF_8) + body
}

internal fun Socket.refuseOutsideNetwork() {
    runCatching { getOutputStream().use { it.write(outsideNetworkResponse()) } }
    close()
}
