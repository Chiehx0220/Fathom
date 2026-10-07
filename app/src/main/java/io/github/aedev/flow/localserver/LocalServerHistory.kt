package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.ViewHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

internal fun HistoryDbHelper.viewHistory() = ViewHistory.getInstance(appContext)

/** Bare video IDs the user has watched, as full watch URLs - matches old `getWatchedUrls()` shape. */
fun HistoryDbHelper.nativeWatchedUrls(): Set<String> =
    runBlocking {
        viewHistory().getAllWatchedVideoIdentities().map { videoIdToUrl(it.videoId, it.serviceId) }.toSet()
    }

/**
 * Create-or-touch a history entry's metadata without disturbing a saved playback position.
 * `uploaderAvatar` is dropped - no column for it in native's history table, and native's history
 * screen doesn't render one either.
 */
fun HistoryDbHelper.nativeSaveToHistory(
    title: String,
    url: String,
    uploader: String,
    thumbnailUrl: String,
    serviceId: Int,
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
            serviceId = serviceId,
        )
    }
}

/** Updates only the progress columns (position/duration) - never clobbers title/thumbnail/channel. */
fun HistoryDbHelper.nativeUpdateWatchProgress(
    videoUrl: String,
    percentWatched: Int,
    durationSeconds: Int,
) {
    val videoId = LocalServerMedia.getVideoId(videoUrl)
    if (videoId.isEmpty()) return
    val durationMs = durationSeconds.toLong() * 1000
    val positionMs = (durationMs * percentWatched.coerceIn(0, 100)) / 100
    runBlocking { viewHistory().updatePlaybackProgress(videoId, positionMs, durationMs) }
}

/** History rows as [InfoItem]s. */
fun HistoryDbHelper.nativeHistory(): List<InfoItem> =
    runBlocking {
        viewHistory().getAllHistory().first().map { entry ->
            val item = StreamInfoItem(entry.serviceId, videoIdToUrl(entry.videoId, entry.serviceId), entry.title, StreamType.VIDEO_STREAM)
            item.setUploaderName(entry.channelName)
            item.setUploaderUrl(if (entry.channelId.isNotEmpty()) channelIdToUrl(entry.channelId, entry.serviceId) else "")
            if (entry.thumbnailUrl.isNotEmpty()) {
                item.thumbnailUrl = entry.thumbnailUrl
            }
            item
        }
    }

fun HistoryDbHelper.nativeRemoveFromHistory(videoUrl: String) {
    val videoId = LocalServerMedia.getVideoId(videoUrl)
    if (videoId.isEmpty()) return
    runBlocking { viewHistory().clearVideoHistory(videoId) }
}

fun HistoryDbHelper.nativeClearHistory() {
    runBlocking { viewHistory().clearAllHistory() }
}

/** History rows for the JSON API: the fields of a video, plus how far the viewer got (0-100). */
fun HistoryDbHelper.nativeHistoryJson(): org.json.JSONArray =
    runBlocking {
        val array = org.json.JSONArray()
        for (entry in viewHistory().getAllHistory().first()) {
            val json = org.json.JSONObject()
            json.put("id", entry.videoId)
            json.put("url", videoIdToUrl(entry.videoId, entry.serviceId))
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
