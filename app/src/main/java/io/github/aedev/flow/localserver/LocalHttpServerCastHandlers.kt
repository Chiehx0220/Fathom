package io.github.aedev.flow.localserver

import io.github.aedev.flow.player.dlna.DlnaCastManager
import io.github.aedev.flow.player.dlna.DlnaDevice
import java.io.OutputStream

private const val CAST_START_TIMEOUT_MS = 15_000L
private const val CAST_POLL_MS = 250L
private const val SEEK_SETTLE_MS = 1_500L
private const val MIN_SEEK_SECONDS = 5L

/** [DlnaDevice.usn] is the id sent back to start a cast. */
internal fun castStateJson(
    devices: List<DlnaDevice>,
    discovering: Boolean,
    casting: DlnaDevice?,
): String {
    val json = HtmlRendererCommon::jsonString
    val list = devices.joinToString(",") { "{\"name\":${json(it.friendlyName)},\"usn\":${json(it.usn)}}" }
    return "{\"discovering\":$discovering,\"casting\":${casting?.let { json(it.usn) } ?: "null"},\"devices\":[$list]}"
}

/** `scan=1` starts discovery if idle; reported as running before its coroutine starts. */
internal fun ClientHandler.handleCastDevices(
    os: OutputStream,
    params: Map<String, String>,
) {
    val scan = params["scan"] == "1"
    if (scan && !DlnaCastManager.isDiscovering.value) DlnaCastManager.startDiscovery(dbHelper.appContext)
    val discovering = scan || DlnaCastManager.isDiscovering.value
    sendResponse(
        os,
        200,
        castStateJson(DlnaCastManager.devices.value, discovering, DlnaCastManager.currentDevice.value),
        "application/json",
    )
}

internal fun ClientHandler.handleCastStart(
    os: OutputStream,
    params: Map<String, String>,
) {
    val url = params["id"].orEmpty().trim()
    val device = DlnaCastManager.devices.value.firstOrNull { it.usn == params["usn"] }
    if (device == null || url.isEmpty()) {
        sendResponse(os, 404, "{\"status\":\"no_device\"}", "application/json")
        return
    }
    try {
        val info = LocalServerSource.streamInfo(dbHelper.appContext, getServiceId(params), url)
        DlnaCastManager.castStreamInfo(device, params["title"].orEmpty().ifEmpty { info.name }, info, null)
        if (!awaitCasting(device)) {
            sendResponse(os, 502, "{\"status\":\"failed\"}", "application/json")
            return
        }
        val position = params["t"]?.toDoubleOrNull()?.toLong() ?: 0L
        if (position >= MIN_SEEK_SECONDS) {
            Thread.sleep(SEEK_SETTLE_MS)
            DlnaCastManager.seekTo(device, position)
        }
        sendResponse(os, 200, "{\"status\":\"success\"}", "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

internal fun ClientHandler.handleCastStop(os: OutputStream) {
    DlnaCastManager.disconnect()
    sendResponse(os, 200, "{\"status\":\"success\"}", "application/json")
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
