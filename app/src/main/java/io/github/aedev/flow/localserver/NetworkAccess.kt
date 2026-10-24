package io.github.aedev.flow.localserver

import java.net.InetAddress

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
