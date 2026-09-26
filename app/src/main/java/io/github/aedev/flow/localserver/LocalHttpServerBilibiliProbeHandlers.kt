package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliPlayback
import io.github.aedev.flow.bilibili.BilibiliSession
import io.github.aedev.flow.bilibili.BilibiliStreamFormat
import io.github.aedev.flow.localserver.LocalHttpServer.ClientHandler
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.OutputStream

// Manual probe for the native Bilibili client (io.github.aedev.flow.bilibili): hits the real
// Bilibili API through it and reports, step by step, what worked and how fast the CDN answers.
// Nothing here plays anything; it exists to verify the ported risk-control logic before the
// player is switched over. Read-only, returns no cookie values.
//
//   GET /api/v1/bilibili_probe?bvid=BV...&page=1

// One session for the whole process so the cookie set and WBI key are reused between probes, the
// way real playback would reuse them.
private var probeSession: BilibiliSession? = null

private fun probeApi(http: OkHttpClient): BilibiliApi {
    val session = probeSession ?: BilibiliSession(http).also { probeSession = it }
    return BilibiliApi(session)
}

private fun Throwable.describe(): JsonObject =
    buildJsonObject {
        put("type", this@describe::class.java.simpleName)
        put("message", message ?: "")
    }

private const val PROBE_BYTES = 512 * 1024

/** Range-requests the first [PROBE_BYTES] of one media URL and reports status and throughput. */
private fun probeCdn(
    http: OkHttpClient,
    url: String,
    headers: Map<String, String>,
): JsonObject =
    buildJsonObject {
        put("host", runCatching { java.net.URI(url).host }.getOrDefault("?"))
        try {
            val builder = Request.Builder().url(url).header("Range", "bytes=0-${PROBE_BYTES - 1}")
            headers.forEach { (k, v) -> builder.header(k, v) }
            val started = System.nanoTime()
            http.newCall(builder.build()).execute().use { response ->
                put("status", response.code)
                var total = 0L
                if (response.isSuccessful) {
                    val buffer = ByteArray(16 * 1024)
                    val stream = response.body!!.byteStream()
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        total += n
                    }
                }
                val ms = (System.nanoTime() - started) / 1_000_000
                put("bytes", total)
                put("ms", ms)
                put("kbps", if (ms > 0) total * 8 / ms else 0L)
            }
        } catch (e: Exception) {
            put("error", e.describe())
        }
    }

private fun BilibiliStreamFormat.summary(): JsonObject =
    buildJsonObject {
        put("qn", id)
        put("codecs", codecs)
        put("width", width)
        put("height", height)
        put("bandwidth", bandwidth)
        put("backupUrls", backupUrls.size)
        put("hasInitRange", initRange != null)
        put("hasIndexRange", indexRange != null)
    }

private fun probePlayback(
    http: OkHttpClient,
    playback: BilibiliPlayback,
): JsonObject =
    buildJsonObject {
        put("title", playback.info.title)
        put("durationSec", playback.info.durationSec)
        put("parts", playback.info.pages.size)
        put("isPaid", playback.info.isPaid)
        put("video", JsonArray(playback.videoFormats.map { it.summary() }))
        put("audio", JsonArray(playback.audioFormats.map { it.summary() }))
        put("requestHeaderNames", JsonArray(playback.requestHeaders.keys.map { JsonPrimitive(it) }))

        // The CDN test is the part that matters for "does playback actually work": highest-quality
        // video and audio, primary URL and every backup, with the same headers playback would send.
        val cdn = mutableListOf<JsonElement>()

        fun test(
            label: String,
            format: BilibiliStreamFormat?,
        ) {
            if (format == null) return
            (listOf(format.url) + format.backupUrls).forEachIndexed { i, url ->
                cdn +=
                    buildJsonObject {
                        put("what", "$label ${if (i == 0) "primary" else "backup#$i"} qn=${format.id}")
                        probeCdn(http, url, playback.requestHeaders).forEach { (k, v) -> put(k, v) }
                    }
            }
        }
        test("video", playback.videoFormats.maxByOrNull { it.height })
        test("audio", playback.audioFormats.firstOrNull())
        put("cdn", JsonArray(cdn))
    }

internal fun ClientHandler.handleApiBilibiliProbe(
    os: OutputStream,
    params: Map<String, String>,
) {
    val bvid = params["bvid"]
    if (bvid.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'bvid' parameter"), "application/json")
        return
    }
    val page = params["page"]?.toIntOrNull() ?: 1
    val http = localServerEntryPoint(dbHelper.appContext).okHttpClient()
    val started = System.currentTimeMillis()

    val result =
        buildJsonObject {
            put("bvid", bvid)
            put("page", page)
            try {
                val playback = runBlocking { probeApi(http).playback(bvid, page) }
                put("ok", true)
                put("playback", probePlayback(http, playback))
            } catch (e: Exception) {
                LocalHttpServer.log("Bilibili probe failed: " + e.message)
                put("ok", false)
                put("error", e.describe())
            }
            put("totalMs", System.currentTimeMillis() - started)
        }
    sendResponse(os, 200, result.toString(), "application/json")
}
