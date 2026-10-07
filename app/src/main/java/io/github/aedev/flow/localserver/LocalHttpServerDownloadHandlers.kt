package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.video.downloader.request.toDownloadRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.OutputStream
import io.github.aedev.flow.data.model.Video as FlowVideo

// Web "Download" button -> DownloadController, the same queue as the app's Quick Actions download.
// The queue worker re-extracts and selects the stream; this only describes the video and quality.
// YouTube only: the button is not rendered for other services (app/js/watch.js).

// Web-facing state names for the button. "none" also covers CANCELLED (nothing left to show).
private fun DownloadItemStatus.webState(): String =
    when (this) {
        DownloadItemStatus.PENDING -> "pending"
        DownloadItemStatus.DOWNLOADING -> "downloading"
        DownloadItemStatus.PAUSED -> "paused"
        DownloadItemStatus.COMPLETED -> "completed"
        DownloadItemStatus.FAILED -> "failed"
        DownloadItemStatus.CANCELLED -> "none"
    }

private fun downloadStateJson(
    state: String,
    progress: Int,
): String = "{\"state\":\"$state\",\"progress\":$progress}"

private fun HistoryDbHelper.downloadStateFor(videoId: String): String {
    val manager = localServerEntryPoint(appContext).videoDownloadManager()
    val download = runBlocking { manager.getDownloadWithItems(videoId) } ?: return downloadStateJson("none", 0)
    val state = download.overallStatus.webState()
    // Room's byte counts are stale mid-download (see VideoDownloadManager.latestProgress); use the
    // live cache while active, else fall back to the persisted (and by then correct) progress.
    val live = if (state == "downloading" || state == "pending") manager.latestProgress(videoId) else null
    val progress = ((live?.progress ?: download.progress) * 100).toInt().coerceIn(0, 100)
    return downloadStateJson(state, progress)
}

// Enqueues at the default download quality. Returns null on success, else a user-facing error.
private fun HistoryDbHelper.startNativeDownload(
    serviceId: Int,
    mediaUrl: String,
    videoId: String,
): String? {
    val info = LocalServerSource.streamInfo(appContext, serviceId, mediaUrl)
    val prefs = PlayerPreferences(appContext)
    val targetHeight = runBlocking { prefs.defaultDownloadQuality.first() }.height

    val video =
        FlowVideo(
            id = videoId,
            title = info.name?.ifBlank { null } ?: "Unknown",
            channelName = info.uploaderName ?: "",
            channelId = info.uploaderUrl?.substringAfterLast("/") ?: "local",
            thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url ?: "",
            duration = info.duration.toInt(),
            viewCount = info.viewCount.coerceAtLeast(0),
            uploadDate = "",
            description = info.description?.content ?: "",
            serviceId = serviceId,
        )

    runBlocking {
        localServerEntryPoint(appContext)
            .downloadController()
            .enqueue(video.toDownloadRequest(targetHeight = targetHeight), replaceExisting = true)
    }
    return null
}

// GET /api/v1/download?action=status|start|cancel|delete&id=<video url>&serviceId=<n>
// Every action answers with the current {"state","progress"} so the button re-renders from one shape.
internal fun ClientHandler.handleApiDownload(
    os: OutputStream,
    params: Map<String, String>,
) {
    val mediaUrl = params["id"]
    if (mediaUrl.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
        return
    }
    val serviceId = getServiceId(params)
    val videoId = LocalServerMedia.getVideoId(mediaUrl)
    if (videoId.isEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Could not determine video id"), "application/json")
        return
    }

    when (params["action"]) {
        "start" -> {
            if (serviceId != 0) {
                sendResponse(os, 400, ApiRenderer.errorJson("Downloads are only supported for YouTube videos"), "application/json")
                return
            }
            val error =
                try {
                    dbHelper.startNativeDownload(serviceId, mediaUrl, videoId)
                } catch (e: Exception) {
                    serverLog("Download start failed: " + e.message)
                    "Could not start download: " + (e.message ?: "unknown error")
                }
            if (error != null) {
                sendResponse(os, 500, ApiRenderer.errorJson(error), "application/json")
                return
            }
            // Service creates its DB rows asynchronously - report "pending" rather than "none" so
            // the button flips immediately instead of waiting for the first poll.
            sendResponse(os, 200, downloadStateJson("pending", 0), "application/json")
        }

        "cancel" -> {
            localServerEntryPoint(dbHelper.appContext).downloadController().cancel(videoId)
            sendResponse(os, 200, downloadStateJson("none", 0), "application/json")
        }

        "delete" -> {
            val manager = localServerEntryPoint(dbHelper.appContext).videoDownloadManager()
            runBlocking { manager.deleteDownload(videoId) }
            sendResponse(os, 200, downloadStateJson("none", 0), "application/json")
        }

        else -> {
            sendResponse(os, 200, dbHelper.downloadStateFor(videoId), "application/json")
        }
    }
}
