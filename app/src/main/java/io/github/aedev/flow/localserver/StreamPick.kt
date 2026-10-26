package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.stream.VideoStream

/** Stream URL for a plain `/stream` request; null if [lists] has nothing for it. */
internal fun pickStreamUrl(
    lists: StreamLists,
    itag: Int,
    trackId: String?,
    mediaType: String?,
    targetQuality: () -> String,
): String? {
    if (itag == NO_ITAG && mediaType != null) return pickByMediaType(lists, mediaType)
    if (itag != NO_ITAG) pickByItag(lists, itag, trackId)?.let { return it }

    val progressive = lists.videoStreams
    if (progressive.isNotEmpty()) {
        return progressive.bestFit(LocalServerMedia.getResolutionHeight(targetQuality())).content
    }
    lists.hlsOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
    return lists.audioStreams
        .sortedWith(LocalServerMedia.audioTrackPriorityComparator())
        .firstOrNull()
        ?.content
}

internal const val NO_ITAG = -1

/** Itag-less services (Bilibili): manifest tags each Representation with its media type. */
private fun pickByMediaType(
    lists: StreamLists,
    mediaType: String,
): String? =
    when (mediaType) {
        "audio" -> {
            lists.audioStreams.maxByOrNull { it.averageBitrate }?.content
        }

        "video" -> {
            lists.videoOnlyStreams
                .ifEmpty { lists.videoStreams }
                .firstOrNull()
                ?.content
        }

        else -> {
            null
        }
    }

private fun pickByItag(
    lists: StreamLists,
    itag: Int,
    trackId: String?,
): String? =
    lists.videoStreams.firstOrNull { it.itag == itag }?.content
        ?: lists.videoOnlyStreams.firstOrNull { it.itag == itag }?.content
        ?: lists.audioStreams.firstOrNull { it.itag == itag && (it.audioTrackId ?: "") == (trackId ?: "") }?.content

/** Tallest stream within [targetHeight]; else the tallest overall; else the first. */
private fun List<VideoStream>.bestFit(targetHeight: Int): VideoStream {
    fun height(stream: VideoStream) = LocalServerMedia.getResolutionHeight(stream.resolution)

    fun tallest(candidates: List<VideoStream>) = candidates.filter { height(it) > -1 }.maxByOrNull(::height)
    return tallest(filter { height(it) <= targetHeight }) ?: tallest(this) ?: first()
}

private fun StreamLists.hlsOrNull(): String? =
    try {
        hlsUrl
    } catch (_: Exception) {
        null
    }
