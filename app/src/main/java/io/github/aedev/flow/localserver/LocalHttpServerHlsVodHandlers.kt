package io.github.aedev.flow.localserver

import java.io.OutputStream
import java.net.URLEncoder

// Video on demand as HLS: the representations of a video, as an HLS master playlist and a media playlist per file.

private const val HLS_TYPE = "application/vnd.apple.mpegurl"

@Throws(Exception::class)
internal fun ClientHandler.handleHlsVod(
    os: OutputStream,
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
        sendResponse(os, 200, HlsVod.master(catalog) { "$base&rep=${enc(it)}" }, HLS_TYPE)
        return
    }

    // Any quality of the file, not only the ones the default offer lists.
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
        sendResponse(os, 404, "Unknown representation.", "text/plain; charset=UTF-8")
        return
    }
    val (init, index) = ranges
    val key = repCacheKey(serviceId, mediaUrl, repId)
    val sidx =
        fetchFromCdn(fileUrl, key, "bytes=${index.start}-${index.end}").use { r ->
            if (r.code != 206 && r.code != 200) {
                sendResponse(os, 502, "The segment index could not be fetched (${r.code}).", "text/plain; charset=UTF-8")
                return
            }
            r.body?.bytes() ?: ByteArray(0)
        }
    val spans =
        try {
            SidxParser.parse(sidx, index.end)
        } catch (e: IllegalArgumentException) {
            sendResponse(os, 502, "The segment index is not usable: ${e.message}", "text/plain; charset=UTF-8")
            return
        }
    sendResponse(os, 200, HlsVod.media(StreamPath.of(serviceId, mediaUrl, repId), init, spans), HLS_TYPE)
}

private fun warmRep(
    catalog: StreamCatalog,
    serviceId: Int,
    mediaUrl: String,
    repId: String,
) {
    catalog.urlOf(repId)?.let { LocalServerCaches.streamUrlCache.put(repCacheKey(serviceId, mediaUrl, repId), it, 3600000) }
}

private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")
