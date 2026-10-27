package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.ViewHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal fun HistoryDbHelper.viewHistory() = ViewHistory.getInstance(appContext)

/** Watched bare video IDs as full watch URLs (old `getWatchedUrls()` shape). */
fun HistoryDbHelper.watchedUrls(): Set<String> =
    runBlocking {
        viewHistory().getAllWatchedVideoIdentities().map { videoIdToUrl(it.videoId, it.serviceId) }.toSet()
    }

/** Create-or-touch history entry, saved position kept. `uploaderAvatar` dropped (no column). */
fun HistoryDbHelper.saveToHistory(
    title: String,
    url: String,
    uploader: String,
    thumbnailUrl: String,
    @Suppress("UNUSED_PARAMETER") serviceId: Int,
    uploaderUrl: String?,
    @Suppress("UNUSED_PARAMETER") uploaderAvatar: String?,
) {
    val videoId = LocalServerMedia.getVideoId(url)
    if (videoId.isEmpty()) return
    runBlocking {
        viewHistory().touchHistoryEntry(
            videoId = videoId,
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = uploader,
            channelId = channelUrlToId(uploaderUrl) ?: "",
        )
    }
}

/** Progress columns only (position/duration); title/thumbnail/channel untouched. */
fun HistoryDbHelper.updateWatchProgress(
    videoUrl: String,
    percentWatched: Int,
    durationSeconds: Int,
) {
    val durationMs = durationSeconds.toLong() * 1000
    updateWatchPosition(videoUrl, (durationMs * percentWatched.coerceIn(0, 100)) / 100, durationMs)
}

fun HistoryDbHelper.updateWatchPosition(
    videoUrl: String,
    positionMs: Long,
    durationMs: Long,
) {
    val videoId = LocalServerMedia.getVideoId(videoUrl)
    if (videoId.isEmpty()) return
    runBlocking { viewHistory().updatePlaybackProgress(videoId, positionMs, durationMs) }
}

fun HistoryDbHelper.removeFromHistory(videoUrl: String) {
    val videoId = LocalServerMedia.getVideoId(videoUrl)
    if (videoId.isEmpty()) return
    runBlocking { viewHistory().clearVideoHistory(videoId) }
}

fun HistoryDbHelper.clearHistory() {
    runBlocking { viewHistory().clearAllHistory() }
}

/** History rows: video fields + progress (0-100). */
internal fun HistoryDbHelper.historyCards(): List<VideoCard> =
    runBlocking {
        // Two rows possible (whole link, pre-normalisation): latest wins.
        val seen = HashSet<String>()
        viewHistory().getAllHistory().first().mapNotNull { entry ->
            val url = videoIdToUrl(entry.videoId, entry.serviceId)
            if (!seen.add(url)) return@mapNotNull null
            VideoCard(
                id = LocalServerMedia.getVideoId(entry.videoId),
                url = url,
                serviceId = entry.serviceId,
                title = entry.title,
                channelName = entry.channelName,
                channelId = if (entry.channelId.isNotEmpty()) channelIdToUrl(entry.channelId, entry.serviceId) else "",
                thumbnailUrl = WebFormat.getThumbnailUrl(entry.thumbnailUrl),
                channelThumbnailUrl = "",
                duration = (entry.duration / 1000).toInt(),
                viewCount = -1,
                uploadDate = "",
                isLive = false,
                isShort = entry.isShort,
                progress = entry.progressPercentage.toInt().coerceIn(0, 100),
            )
        }
    }
