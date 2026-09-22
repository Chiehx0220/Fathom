package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.model.Video as FlowVideo
import io.github.aedev.flow.data.video.downloader.FlowDownloadService
import io.github.aedev.flow.player.stream.AudioStreamSelector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import io.github.aedev.flow.localserver.LocalHttpServer.ClientHandler
import java.io.OutputStream

// Web "Download" button -> Flow's own FlowDownloadService/VideoDownloadManager (the same pipeline
// the app's Quick Actions download uses), so files land in the same place, show up in the app's
// Downloads screen, and the same progress/notification machinery applies. Nothing here downloads
// anything itself. YouTube only: Flow's downloader has no Referer/header plumbing for other
// services' CDNs, so the button is not rendered for them (HtmlRendererWatch.kt).

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

private fun downloadStateJson(state: String, progress: Int): String =
    "{\"state\":\"$state\",\"progress\":$progress}"

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

// Mirrors QuickActionsViewModel.downloadVideo()'s NewPipe-StreamInfo fallback branch (best MP4
// video-only <= the user's default download quality + preferred-language AAC audio, or a combined
// progressive stream when that's taller/only option), minus its InnerTube/SABR/VP9 fallbacks - a
// video that would need those fails here with a message rather than silently doing something
// different from the app. Returns null on success, else a user-facing error.
private fun HistoryDbHelper.startNativeDownload(serviceId: Int, mediaUrl: String, videoId: String): String? {
    val info = LocalServerSource.streamInfo(appContext, serviceId, mediaUrl)

    val prefs = PlayerPreferences(appContext)
    val targetHeight = runBlocking { prefs.defaultDownloadQuality.first() }.height
    val preferredAudioLanguage = runBlocking { prefs.preferredAudioLanguage.first() }

    fun heightOf(s: VideoStream) = LocalHttpServer.getResolutionHeight(s.resolution)

    fun List<VideoStream>.bestForTarget(): VideoStream? {
        if (isEmpty()) return null
        if (targetHeight == 0) return maxByOrNull(::heightOf)
        return filter { heightOf(it) <= targetHeight }.maxByOrNull(::heightOf) ?: minByOrNull(::heightOf)
    }

    val bestMp4VideoOnly = (info.videoOnlyStreams ?: emptyList()).filter { it.format == MediaFormat.MPEG_4 }.bestForTarget()
    val bestCombined = (info.videoStreams ?: emptyList()).bestForTarget()

    val useVideoOnly = bestMp4VideoOnly != null && (bestCombined == null || heightOf(bestMp4VideoOnly) > heightOf(bestCombined))
    val selected = (if (useVideoOnly) bestMp4VideoOnly else bestCombined)
        ?: return "No downloadable stream found for this video"
    val videoUrl = selected.content?.takeIf { it.isNotBlank() } ?: return "No downloadable stream found for this video"

    var audioUrl: String? = null
    if (useVideoOnly) {
        val audio = AudioStreamSelector.selectPreferredAudioStream(
            streams = info.audioStreams ?: emptyList(),
            preferredAudioLanguage = preferredAudioLanguage,
            compatibilityFilter = { it.format == MediaFormat.M4A },
        )
        audioUrl = audio?.content?.takeIf { it.isNotBlank() } ?: return "No compatible audio stream found for this video"
    }

    val video = FlowVideo(
        id = videoId,
        title = info.name?.ifBlank { null } ?: "Unknown",
        channelName = info.uploaderName ?: "",
        channelId = info.uploaderUrl?.substringAfterLast("/") ?: "local",
        thumbnailUrl = info.thumbnails?.maxByOrNull { it.height }?.url ?: "",
        duration = info.duration.toInt(),
        viewCount = info.viewCount.coerceAtLeast(0),
        uploadDate = "",
        description = info.description?.content ?: "",
        serviceId = serviceId,
    )

    // startForegroundService can be refused while the app is in the background (Android 12+
    // background-start rules) - surfaced to the page instead of failing silently.
    return try {
        FlowDownloadService.startDownload(
            context = appContext,
            video = video,
            url = videoUrl,
            quality = "${heightOf(selected)}p",
            audioUrl = audioUrl,
        )
        null
    } catch (e: Exception) {
        LocalHttpServer.log("Download start refused: " + e.message)
        "Android blocked starting the download while Flow is in the background - open the Flow app and try again"
    }
}

// GET /api/v1/download?action=status|start|cancel|delete&id=<video url>&serviceId=<n>
// Every action answers with the current {"state","progress"} so the button re-renders from one shape.
internal fun ClientHandler.handleApiDownload(os: OutputStream, params: Map<String, String>) {
    val mediaUrl = params["id"]
    if (mediaUrl.isNullOrEmpty()) {
        sendResponse(os, 400, ApiRenderer.errorJson("Missing 'id' parameter"), "application/json")
        return
    }
    val serviceId = getServiceId(params)
    val videoId = LocalHttpServer.getVideoId(mediaUrl)
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
            val error = try {
                dbHelper.startNativeDownload(serviceId, mediaUrl, videoId)
            } catch (e: Exception) {
                LocalHttpServer.log("Download start failed: " + e.message)
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
            FlowDownloadService.cancelDownload(dbHelper.appContext, videoId)
            sendResponse(os, 200, downloadStateJson("none", 0), "application/json")
        }
        "delete" -> {
            val manager = localServerEntryPoint(dbHelper.appContext).videoDownloadManager()
            runBlocking { manager.deleteDownload(videoId) }
            sendResponse(os, 200, downloadStateJson("none", 0), "application/json")
        }
        else -> sendResponse(os, 200, dbHelper.downloadStateFor(videoId), "application/json")
    }
}
