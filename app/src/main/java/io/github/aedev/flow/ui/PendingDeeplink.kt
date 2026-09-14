package io.github.aedev.flow.ui

import org.schabi.newpipe.extractor.ServiceList

/**
 * A video deep link MainActivity is waiting for the nav graph to pick up and consume.
 *
 * serviceId always travels bundled with videoId instead of as a sibling field a caller could set
 * (or forget to set) independently - a sibling-field split here is exactly what let the quick-panel
 * reopen path fall back to serviceId's default (YouTube) for a non-YouTube video.
 */
data class PendingDeeplink(
    val videoId: String,
    val serviceId: Int = ServiceList.YouTube.serviceId,
    val isShort: Boolean = false,
)
