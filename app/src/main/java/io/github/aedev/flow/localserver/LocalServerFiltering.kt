package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private const val SHORTS_MAX_SECONDS = 120L

/** [items] without watched / short / blocked-channel entries, per the user's settings. */
internal fun HistoryDbHelper.withoutHidden(items: List<InfoItem>): List<InfoItem> {
    val hideWatched = hideWatched()
    val hideShorts = hideShorts()
    val blockedChannelIds = blockedChannelIds()
    val watchedUrls: Set<String> = if (hideWatched) watchedUrls() else emptySet()

    return items.filterNot { item ->
        when {
            hideWatched && item.url in watchedUrls -> true
            item !is StreamInfoItem -> false
            hideShorts && item.duration in 1..SHORTS_MAX_SECONDS -> true
            blockedChannelIds.isEmpty() -> false
            else -> channelUrlToId(item.uploaderUrl)?.let { it in blockedChannelIds } == true
        }
    }
}
