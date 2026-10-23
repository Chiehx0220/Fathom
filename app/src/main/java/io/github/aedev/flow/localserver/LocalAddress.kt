package io.github.aedev.flow.localserver

import java.net.Inet4Address
import java.net.NetworkInterface

private val LAN_INTERFACES = listOf("wlan", "eth", "swlan", "ap", "softap")
private val NON_LAN_INTERFACES = listOf("rmnet", "ccmni", "tun", "ppp", "dummy", "v4-", "clat")

/** IPv4 address a same-network browser reaches; null when none is up. */
internal fun lanAddress(): String? =
    try {
        preferredLanAddress(lanCandidates())
    } catch (e: Exception) {
        null
    }

private fun lanCandidates(): List<Pair<String, String>> =
    NetworkInterface
        .getNetworkInterfaces()
        .toList()
        .filter { it.isUp }
        .flatMap { network ->
            network.inetAddresses
                .toList()
                .filterIsInstance<Inet4Address>()
                .filterNot { it.isLoopbackAddress }
                .map { network.name to it.hostAddress.orEmpty() }
        }

/** Wi-Fi, ethernet, then hotspot interfaces; mobile data and tunnels are excluded. */
internal fun preferredLanAddress(candidates: List<Pair<String, String>>): String? {
    val usable = candidates.filter { (name, _) -> NON_LAN_INTERFACES.none(name::startsWith) }
    return LAN_INTERFACES
        .firstNotNullOfOrNull { prefix -> usable.firstOrNull { it.first.startsWith(prefix) } }
        ?.second
        ?: usable.firstOrNull()?.second
}
