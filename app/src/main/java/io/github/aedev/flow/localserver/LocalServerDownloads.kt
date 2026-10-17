package io.github.aedev.flow.localserver

import android.net.Uri
import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.data.local.entity.DownloadFileType
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.video.DownloadStreamPolicy
import io.github.aedev.flow.data.video.storage.DownloadFiles
import io.github.aedev.flow.player.stream.VideoCodecUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// GET /api/v1/downloads: finished downloads, newest first
// GET /downloadfile?serviceId&id: saved file, Range supported; path from download row only

internal class DownloadQuality(
    val height: Int,
    val codecs: List<String>,
)

/** Downloadable heights, tallest first, each with codec keys in preference order. */
internal fun downloadQualities(streams: List<VideoStream>): List<DownloadQuality> {
    val byHeight = streams.filter { it.height > 0 }.groupBy { it.height }
    return byHeight.keys.sortedDescending().map { height ->
        val codecs =
            byHeight
                .getValue(height)
                .map { VideoCodecUtils.codecKeyFromMimeType("video/mp4; codecs=\"${it.codec.orEmpty()}\"") }
                .distinct()
                .sortedBy { DownloadStreamPolicy.DOWNLOAD_CODEC_PRIORITY[it] ?: Int.MAX_VALUE }
        DownloadQuality(height, codecs)
    }
}

/** Video item, else audio item, of a finished download. */
private fun DownloadWithItems.playableItem(): DownloadItemEntity? =
    items.firstOrNull { it.fileType == DownloadFileType.VIDEO && it.status == DownloadItemStatus.COMPLETED }
        ?: items.firstOrNull { it.fileType == DownloadFileType.AUDIO && it.status == DownloadItemStatus.COMPLETED }

private fun HistoryDbHelper.finishedDownloads(): List<DownloadWithItems> {
    val all = runBlocking { localServerEntryPoint(appContext).videoDownloadManager().allDownloads.first() }
    return all
        .filter { it.overallStatus == DownloadItemStatus.COMPLETED }
        .filter { download -> download.playableItem()?.let { DownloadFiles.exists(appContext, it.filePath) } == true }
        .sortedByDescending { it.download.createdAt }
}

internal fun ClientHandler.handleApiDownloads(os: OutputStream) {
    try {
        val array = JSONArray()
        for (entry in dbHelper.finishedDownloads()) {
            val download = entry.download
            val id = LocalServerMedia.getVideoId(download.videoId)
            // recovered rows lack serviceId: derived from id
            val serviceId = serviceIdOfVideo(id, download.serviceId)
            val json = JSONObject()
            json.put("id", id)
            json.put("url", videoIdToUrl(download.videoId, serviceId))
            json.put("serviceId", serviceId)
            json.put("title", download.title)
            json.put("channelName", download.uploader)
            json.put("channelId", if (download.channelId.isNotEmpty()) channelIdToUrl(download.channelId, serviceId) else "")
            json.put("thumbnailUrl", HtmlRendererCommon.getThumbnailUrl(download.thumbnailUrl))
            json.put("channelThumbnailUrl", "")
            json.put("duration", download.duration.toInt())
            json.put("viewCount", -1)
            json.put("uploadDate", "")
            json.put("isLive", false)
            json.put("isShort", false)
            json.put("progress", 0)
            // opened from saved file (watch?...&local=1)
            json.put("local", true)
            json.put("sizeBytes", entry.totalSize)
            array.put(json)
        }
        sendResponse(os, 200, JSONObject().put("videos", array).toString(), "application/json")
    } catch (e: Exception) {
        sendResponse(os, 500, ApiRenderer.errorJson(e.message), "application/json")
    }
}

/** Length + stream at offset (file or SAF document). */
private class DownloadSource(
    val length: Long,
    private val open: (Long) -> InputStream,
) {
    fun streamFrom(start: Long): InputStream = open(start)
}

private fun HistoryDbHelper.sourceOf(path: String): DownloadSource? {
    if (!DownloadFiles.isDocument(path)) {
        val file = File(path)
        if (!file.isFile) return null
        return DownloadSource(file.length()) { start ->
            FileInputStream(file).also { it.channel.position(start) }
        }
    }
    val resolver = appContext.contentResolver
    val uri = Uri.parse(path)
    val length = runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: return null
    return DownloadSource(length) { start ->
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: throw IOException("The document cannot be opened")
        object : FileInputStream(descriptor.fileDescriptor) {
            init {
                channel.position(start)
            }

            override fun close() {
                super.close()
                descriptor.close()
            }
        }
    }
}

private fun contentTypeOf(path: String): String =
    when (path.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v", "mov" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "m4a", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "ogg", "opus" -> "audio/ogg"
        else -> "application/octet-stream"
    }

/** Single range (`a-b`, `a-`, `-n`) as inclusive bounds; null if unsatisfiable. */
internal fun parseByteRange(
    header: String?,
    length: Long,
): LongRange? {
    if (header == null || !header.startsWith("bytes=") || length <= 0) return null
    val spec = header.removePrefix("bytes=").substringBefore(',').trim()
    val dash = spec.indexOf('-')
    if (dash < 0) return null
    val from = spec.substring(0, dash).toLongOrNull()
    val to = spec.substring(dash + 1).toLongOrNull()
    if (from == null) {
        // suffix range
        return if (to == null || to <= 0) null else maxOf(0, length - to) until length
    }
    if (from >= length) return null
    val last = minOf(to ?: (length - 1), length - 1)
    return if (last >= from) from..last else null
}

internal fun ClientHandler.handleDownloadFile(
    os: OutputStream,
    params: Map<String, String>,
    requestHeaders: Map<String, String>,
) {
    val videoId = params["id"]?.let(LocalServerMedia::getVideoId).orEmpty()
    if (videoId.isEmpty()) {
        sendResponse(os, 400, "Missing 'id' parameter.", "text/plain; charset=UTF-8")
        return
    }
    val download = runBlocking { localServerEntryPoint(dbHelper.appContext).videoDownloadManager().getDownloadWithItems(videoId) }
    val item = download?.takeIf { it.overallStatus == DownloadItemStatus.COMPLETED }?.playableItem()
    val source = item?.let { dbHelper.sourceOf(it.filePath) }
    if (item == null || source == null) {
        sendResponse(os, 404, "This video is not downloaded.", "text/plain; charset=UTF-8")
        return
    }

    val rangeHeader = requestHeaders.entries.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
    val range = parseByteRange(rangeHeader, source.length)
    if (rangeHeader != null && range == null && source.length > 0) {
        val head = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */${source.length}\r\nContent-Length: 0\r\n\r\n"
        os.write(head.toByteArray(Charsets.UTF_8))
        os.flush()
        return
    }
    val start = range?.first ?: 0L
    val end = range?.last ?: (source.length - 1)
    val count = if (source.length == 0L) 0L else end - start + 1

    val head =
        StringBuilder()
            .append(if (range != null) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            .append("Content-Type: ")
            .append(contentTypeOf(item.filePath))
            .append("\r\n")
            .append("Accept-Ranges: bytes\r\n")
            .append("Content-Length: ")
            .append(count)
            .append("\r\n")
    if (range !=
        null
    ) {
        head
            .append("Content-Range: bytes ")
            .append(start)
            .append('-')
            .append(end)
            .append('/')
            .append(source.length)
            .append("\r\n")
    }
    head.append("\r\n")
    os.write(head.toString().toByteArray(Charsets.UTF_8))
    os.flush()

    if (count == 0L) return
    try {
        source.streamFrom(start).use { input ->
            val buffer = ByteArray(64 * 1024)
            var left = count
            while (left > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (read < 0) break
                os.write(buffer, 0, read)
                left -= read
            }
            os.flush()
        }
    } catch (e: IOException) {
        // client closed the connection (seek)
    }
}
