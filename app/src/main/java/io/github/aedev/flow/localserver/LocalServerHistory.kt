package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.ViewHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

internal fun HistoryDbHelper.viewHistory() = ViewHistory.getInstance(appContext)

/** Watched bare video IDs as full watch URLs (old `getWatchedUrls()` shape). */
fun HistoryDbHelper.nativeWatchedUrls(): Set<String> =
    runBlocking {
        viewHistory().getAllWatchedVideoIdentities().map { videoIdToUrl(it.videoId, it.serviceId) }.toSet()
    }

/**
 * Create-or-touch history entry metadata, saved position kept. `uploaderAvatar` dropped (no column in native history table; native history screen shows none).
 */
fun HistoryDbHelper.nativeSaveToHistory(
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
fun HistoryDbHelper.nativeUpdateWatchProgress(
    videoUrl: String,
    percentWatched: Int,
    durationSeconds: Int,
) {
    val durationMs = durationSeconds.toLong() * 1000
    nativeUpdateWatchPosition(videoUrl, (durationMs * percentWatched.coerceIn(0, 100)) / 100, durationMs)
}

fun HistoryDbHelper.nativeUpdateWatchPosition(
    videoUrl: String,
    positionMs: Long,
    durationMs: Long,
) {
    val videoId = LocalServerMedia.getVideoId(videoUrl)
    if (videoId.isEmpty()) return
    runBlocking { viewHistory().updatePlaybackProgress(videoId, positionMs, durationMs) }
}

fun HistoryDbHelper.nativeRemoveFromHistory(videoUrl: String) {
    val videoId = LocalServerMedia.getVideoId(videoUrl)
    if (videoId.isEmpty()) return
    runBlocking { viewHistory().clearVideoHistory(videoId) }
}

fun HistoryDbHelper.nativeClearHistory() {
    runBlocking { viewHistory().clearAllHistory() }
}

/** History rows for the JSON API: video fields + progress (0-100). */
fun HistoryDbHelper.nativeHistoryJson(): org.json.JSONArray =
    runBlocking {
        val array = org.json.JSONArray()
        // Video may have two rows (one under a whole link, from web page before id normalisation): most recent wins.
        val seen = HashSet<String>()
        for (entry in viewHistory().getAllHistory().first()) {
            val url = videoIdToUrl(entry.videoId, entry.serviceId)
            if (!seen.add(url)) continue
            val json = org.json.JSONObject()
            json.put("id", LocalServerMedia.getVideoId(entry.videoId))
            json.put("url", url)
            json.put("serviceId", entry.serviceId)
            json.put("title", entry.title)
            json.put("channelName", entry.channelName)
            json.put("channelId", if (entry.channelId.isNotEmpty()) channelIdToUrl(entry.channelId, entry.serviceId) else "")
            json.put("thumbnailUrl", HtmlRendererCommon.getThumbnailUrl(entry.thumbnailUrl))
            json.put("channelThumbnailUrl", "")
            json.put("duration", (entry.duration / 1000).toInt())
            json.put("viewCount", -1)
            json.put("uploadDate", "")
            json.put("isLive", false)
            json.put("isShort", entry.isShort)
            json.put("progress", entry.progressPercentage.toInt().coerceIn(0, 100))
            array.put(json)
        }
        array
    }
