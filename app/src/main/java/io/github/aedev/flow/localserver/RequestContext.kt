package io.github.aedev.flow.localserver

import java.net.InetAddress
import java.util.concurrent.ExecutorService

// Per-request dependencies; handlers are extension functions in sibling files.
internal class RequestContext(
    internal val peer: InetAddress,
    internal val dbHelper: HistoryDbHelper,
    internal val executor: ExecutorService,
    internal val pairing: Pairing,
    internal val source: ContentSource,
    internal val requestPath: String,
    internal val headers: Map<String, String>,
) {
    internal val remoteAddress: String? get() = peer.hostAddress
    internal val remoteIsLoopback: Boolean get() = peer.isLoopbackAddress

    internal fun getServiceId(params: Query): Int =
        params["serviceId"]?.trim()?.toIntOrNull()?.takeIf(LocalServerMedia::isSupportedService) ?: LocalServerMedia.SERVICE_YOUTUBE
}
