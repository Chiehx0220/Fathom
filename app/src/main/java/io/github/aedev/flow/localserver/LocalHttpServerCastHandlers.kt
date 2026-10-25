package io.github.aedev.flow.localserver

import io.github.aedev.flow.player.dlna.DlnaCastManager
import io.github.aedev.flow.player.dlna.DlnaDevice
import java.io.OutputStream

private const val CAST_START_TIMEOUT_MS = 15_000L
private const val CAST_POLL_MS = 250L
private const val SEEK_SETTLE_MS = 1_500L
private const val MIN_SEEK_SECONDS = 5L

/** [DlnaDevice.usn] is the id sent back to start a cast. */
internal fun castState(
    devices: List<DlnaDevice>,
    discovering: Boolean,
    casting: DlnaDevice?,
) = CastState(discovering, casting?.usn.orJsonNull(), devices.map { CastDeviceDto(it.friendlyName, it.usn) })

/** `scan=1` starts discovery if idle; reported as running before its coroutine starts. */
internal fun RequestContext.handleCastDevices(
    reply: Reply,
    params: Query,
) {
    val scan = params.flag("scan")
    if (scan && !DlnaCastManager.isDiscovering.value) DlnaCastManager.startDiscovery(dbHelper.appContext)
    val discovering = scan || DlnaCastManager.isDiscovering.value
    reply.sendJson(castState(DlnaCastManager.devices.value, discovering, DlnaCastManager.currentDevice.value))
}

internal fun RequestContext.handleCastStart(
    reply: Reply,
    params: Query,
) {
    val url = params["id"].orEmpty().trim()
    val device = DlnaCastManager.devices.value.firstOrNull { it.usn == params["usn"] }
    if (device == null || url.isEmpty()) {
        reply.sendJson(Status("no_device"), 404)
        return
    }
    val info = LocalServerSource.streamInfo(dbHelper.appContext, getServiceId(params), url)
    DlnaCastManager.castStreamInfo(device, params["title"].orEmpty().ifEmpty { info.name }, info, null)
    if (!awaitCasting(device)) {
        reply.sendJson(Status("failed"), 502)
        return
    }
    val position = params.double("t")?.toLong() ?: 0L
    if (position >= MIN_SEEK_SECONDS) {
        Thread.sleep(SEEK_SETTLE_MS)
        DlnaCastManager.seekTo(device, position)
    }
    reply.sendJson(SUCCESS)
}

internal fun RequestContext.handleCastStop(reply: Reply) {
    DlnaCastManager.disconnect()
    reply.sendJson(SUCCESS)
}

private fun awaitCasting(device: DlnaDevice): Boolean {
    var waited = 0L
    while (waited < CAST_START_TIMEOUT_MS) {
        if (DlnaCastManager.currentDevice.value?.usn == device.usn) return true
        Thread.sleep(CAST_POLL_MS)
        waited += CAST_POLL_MS
    }
    return false
}
