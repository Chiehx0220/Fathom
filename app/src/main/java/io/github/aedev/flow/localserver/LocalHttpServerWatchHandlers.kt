package io.github.aedev.flow.localserver

import io.github.aedev.flow.localserver.LocalHttpServer.ClientHandler
import java.io.OutputStream

// Bilibili danmaku ("bullet comments"), fetched async by the watch page's player. Only Bilibili
// exposes a BulletCommentsExtractor (null elsewhere) - other services get an empty result.
@Throws(Exception::class)
internal fun ClientHandler.handleDanmaku(
    os: OutputStream,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"]
    if (mediaUrl.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
        return
    }
    try {
        val danmaku = LocalServerSource.danmaku(dbHelper.appContext, serviceId, mediaUrl)
        val json = org.json.JSONObject()
        json.put("danmaku", danmaku)
        sendResponse(os, 200, json.toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}
