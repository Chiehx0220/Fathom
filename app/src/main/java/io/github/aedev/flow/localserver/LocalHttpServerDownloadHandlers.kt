package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.video.downloader.request.toDownloadRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import io.github.aedev.flow.data.model.Video as FlowVideo

// Web download button → DownloadController (shared queue); worker re-extracts. YouTube only.

// Web button state names. "none" also covers CANCELLED.
private fun DownloadItemStatus.webState(): String =
    when (this) {
        DownloadItemStatus.PENDING -> "pending"
        DownloadItemStatus.DOWNLOADING -> "downloading"
        DownloadItemStatus.PAUSED -> "paused"
        DownloadItemStatus.COMPLETED -> "completed"
        DownloadItemStatus.FAILED -> "failed"
        DownloadItemStatus.CANCELLED -> "none"
    }

private fun HistoryDbHelper.downloadStateFor(videoId: String): DownloadState {
    val manager = localServerEntryPoint(appContext).videoDownloadManager()
    val download = runBlocking { manager.getDownloadWithItems(videoId) } ?: return DownloadState("none", 0)
    val state = download.overallStatus.webState()
    // Room byte counts stale mid-download (VideoDownloadManager.latestProgress): live cache while active, else persisted progress.
    val live = if (state == "downloading" || state == "pending") manager.latestProgress(videoId) else null
    val progress = ((live?.progress ?: download.progress) * 100).toInt().coerceIn(0, 100)
    return DownloadState(state, progress)
}

/** `height`: tallest wanted (0 = best); `codec`: vp9/h264/av1/...; absent: default policy. `audio=1`: audio only. */
internal class DownloadChoice(
    val height: Int?,
    val codec: String?,
    val audioOnly: Boolean,
)

internal fun downloadChoice(params: Query): DownloadChoice =
    DownloadChoice(
        height = params.int("height")?.takeIf { it in 0..MAX_DOWNLOAD_HEIGHT },
        codec = params["codec"]?.lowercase()?.takeIf { it in DOWNLOAD_CODECS },
        audioOnly = params.flag("audio"),
    )

private val DOWNLOAD_CODECS = setOf("vp9", "h264", "av1", "hevc", "vp8")

private const val MAX_DOWNLOAD_HEIGHT = 4320

// Enqueue at [choice] or default download quality. Null on success, else user-facing error.
private fun RequestContext.startNativeDownload(
    serviceId: Int,
    mediaUrl: String,
    videoId: String,
    choice: DownloadChoice,
): String? {
    val info = source.streamInfo(serviceId, mediaUrl)
    val prefs = PlayerPreferences(dbHelper.appContext)
    val targetHeight = choice.height ?: runBlocking { prefs.defaultDownloadQuality.first() }.height

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
        localServerEntryPoint(dbHelper.appContext)
            .downloadController()
            .enqueue(
                video.toDownloadRequest(audioOnly = choice.audioOnly, targetHeight = targetHeight, videoCodec = choice.codec),
                replaceExisting = true,
            )
    }
    return null
}

// GET /api/v1/download?action=status|start|cancel|delete&id=<video url>&serviceId=<n>
// Every action answers current {"state","progress"}.
internal fun RequestContext.handleApiDownload(params: Query): DownloadState {
    val mediaUrl = params.required("id")
    val serviceId = getServiceId(params)
    val videoId = LocalServerMedia.getVideoId(mediaUrl).ifEmpty { throw BadRequestException("Could not determine video id") }

    return when (params["action"]) {
        "start" -> {
            if (serviceId != 0) throw BadRequestException("Downloads are only supported for YouTube videos")
            val error =
                try {
                    startNativeDownload(serviceId, mediaUrl, videoId, downloadChoice(params))
                } catch (e: Exception) {
                    serverLog("Download start failed: " + e.message)
                    "Could not start download: " + (e.message ?: "unknown error")
                }
            if (error != null) fail(500, ErrorBody(error = error))
            // Service creates DB rows async: report "pending", not "none" (button flips before first poll).
            DownloadState("pending", 0)
        }

        "cancel" -> {
            localServerEntryPoint(dbHelper.appContext).downloadController().cancel(videoId)
            DownloadState("none", 0)
        }

        "delete" -> {
            val manager = localServerEntryPoint(dbHelper.appContext).videoDownloadManager()
            runBlocking { manager.deleteDownload(videoId) }
            DownloadState("none", 0)
        }

        else -> {
            dbHelper.downloadStateFor(videoId)
        }
    }
}
