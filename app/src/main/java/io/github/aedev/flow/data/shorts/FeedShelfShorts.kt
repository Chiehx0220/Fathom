package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf

/**
 * The shelves with every Short taken out, for a viewer who switched Shorts off (#1507). A shelf
 * left with nothing is dropped rather than drawn as an empty header.
 */
fun List<FeedShelf>.withoutShorts(): List<FeedShelf> =
    mapNotNull { shelf ->
        val kept = shelf.items.filterNot(FeedItem::isShort)
        when {
            kept.size == shelf.items.size -> shelf
            kept.isEmpty() -> null
            else -> shelf.copy(items = kept)
        }
    }

/** A collaboration reel arrives as a plain video lockup marked as a Short, so both shapes count. */
fun FeedItem.isShort(): Boolean = this is FeedItem.ShortItem || (this is FeedItem.VideoItem && video.isShort)
