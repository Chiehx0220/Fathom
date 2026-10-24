package io.github.aedev.flow.localserver

import io.github.aedev.flow.utils.youtubeVideoIdFromUrl
import java.io.OutputStream
import java.net.URLEncoder

// VOD as HLS: master playlist + media playlist per file.

private const val HLS_TYPE = "application/vnd.apple.mpegurl"

@Throws(Exception::class)
internal fun RequestContext.handleHlsVod(
    reply: Reply,
    params: Map<String, String>,
) {
    val serviceId = getServiceId(params)
    val mediaUrl = params["id"] ?: throw BadRequestException("Missing 'id' parameter")
    val extractor = LocalServerSource.streams(dbHelper.appContext, serviceId, mediaUrl, fresh = false)
    val repId = params["rep"]

    if (repId == null) {
        val catalog = StreamCatalog.of(extractor, preferredTrack = params["audio_track"], highest = params["prefer"] == "hd")
        catalog.videos.forEach { warmRep(catalog, serviceId, mediaUrl, it.id) }
        catalog.audioTracks.forEach { t -> t.audios.forEach { warmRep(catalog, serviceId, mediaUrl, it.id) } }
        val base = "/hlsvod?serviceId=$serviceId&id=${enc(mediaUrl)}"
        reply.send(200, HlsVod.master(catalog) { "$base&rep=${enc(it)}" }, HLS_TYPE)
        return
    }

    // Any quality of the file, not only default-offer ones.
    val catalog = StreamCatalog.of(extractor, highest = true)
    val ranges =
        catalog.videos.firstOrNull { it.id == repId }?.let { it.init to it.index }
            ?: catalog.audioTracks.firstNotNullOfOrNull { t -> t.audios.firstOrNull { it.id == repId } }?.let { it.init to it.index }
            ?: StreamCatalog
                .of(extractor, highest = false)
                .videos
                .firstOrNull { it.id == repId }
                ?.let { it.init to it.index }
    val fileUrl = StreamCatalog.urlOf(extractor, repId)
    if (ranges == null || fileUrl == null) {
        reply.send(404, "Unknown representation.", "text/plain; charset=UTF-8")
        return
    }
    val (init, index) = ranges
    val key = repCacheKey(serviceId, mediaUrl, repId)
    val fetched = fetchIndex(fileUrl, key, "bytes=${index.start}-${index.end}")
    val sidx = fetched.bytes
    if (sidx == null) {
        reply.send(502, "The segment index could not be fetched (${fetched.code}).", "text/plain; charset=UTF-8")
        return
    }
    val spans =
        try {
            SidxParser.parse(sidx, index.end)
        } catch (e: IllegalArgumentException) {
            reply.send(502, "The segment index is not usable: ${e.message}", "text/plain; charset=UTF-8")
            return
        }
    reply.send(200, HlsVod.media(StreamPath.of(serviceId, mediaUrl, repId), init, spans), HLS_TYPE)
}

private class IndexFetch(
    val code: Int,
    val bytes: ByteArray?,
)

/** Range fetch; fresh web-client URL answers 403 for ~1-2 s: retried. */
private fun fetchIndex(
    url: String,
    key: String,
    range: String,
): IndexFetch {
    var code = 0
    for (attempt in 0..INDEX_RETRIES) {
        if (attempt > 0) Thread.sleep(INDEX_RETRY_DELAY_MS)
        val result =
            fetchFromCdn(url, key, range).use { r ->
                code = r.code
                if (r.code == 206 || r.code == 200) r.body?.bytes() ?: ByteArray(0) else null
            }
        if (result != null) return IndexFetch(code, result)
        if (code != 403) break
    }
    return IndexFetch(code, null)
}

private const val INDEX_RETRIES = 3
private const val INDEX_RETRY_DELAY_MS = 1200L

private fun warmRep(
    catalog: StreamCatalog,
    serviceId: Int,
    mediaUrl: String,
    repId: String,
) {
    val url = catalog.urlOf(repId) ?: return
    val key = repCacheKey(serviceId, mediaUrl, repId)
    LocalServerCaches.streamUrlCache.put(key, url, 3600000)
    // UA must match the minting client; web-client URLs reject others
    youtubeVideoIdFromUrl(mediaUrl)
        ?.let { LocalServerYouTubeStreams.extractionFor(it)?.usedClient?.userAgent }
        ?.let { LocalServerCaches.streamUaCache.put(key, it, 3600000) }
}

private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")
