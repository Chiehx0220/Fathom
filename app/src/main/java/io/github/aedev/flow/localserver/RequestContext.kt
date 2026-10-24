package io.github.aedev.flow.localserver

import java.net.InetAddress
import java.util.concurrent.ExecutorService

// Per-request dependencies; handlers are extension functions in sibling files.
internal class RequestContext(
    internal val peer: InetAddress,
    internal val dbHelper: HistoryDbHelper,
    internal val executor: ExecutorService,
    internal val pairing: Pairing,
) {
    internal val remoteAddress: String? get() = peer.hostAddress
    internal val remoteIsLoopback: Boolean get() = peer.isLoopbackAddress

    internal fun getServiceId(params: Map<String, String>): Int {
        val raw = params["serviceId"]
        if (raw != null) {
            try {
                val id = raw.trim().toInt()
                if (LocalServerMedia.isSupportedService(id)) {
                    return id
                }
            } catch (ignored: NumberFormatException) {
            }
        }
        return LocalServerMedia.SERVICE_YOUTUBE
    }
}
