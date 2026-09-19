package io.github.aedev.flow.ui

import androidx.navigation.NavHostController
import org.schabi.newpipe.extractor.ServiceList
import java.net.URLDecoder

/**
 * Most channel-click callbacks in the app carry only the id, not the service it belongs to. An id
 * made purely of digits can only be Bilibili's uploader "mid" (YouTube ids are "UC..." or handles),
 * so it is routed there instead of being sent to YouTube's browse endpoint as an invalid id.
 * Callers that do know the service are unaffected.
 */
internal fun effectiveServiceId(
    channelIdOrHandle: String,
    serviceId: Int,
): Int {
    val id = channelIdOrHandle.trim()
    val isBilibiliMid = id.isNotEmpty() && id.all(Char::isDigit)
    return if (serviceId == ServiceList.YouTube.serviceId && isBilibiliMid) ServiceList.BiliBili.serviceId else serviceId
}

internal fun NavHostController.navigateToYoutubeChannel(
    channelIdOrHandle: String,
    serviceId: Int = ServiceList.YouTube.serviceId,
) {
    val targetUrl = youtubeChannelUrl(channelIdOrHandle, effectiveServiceId(channelIdOrHandle, serviceId)) ?: return
    val currentUrl = currentBackStackEntry
        ?.takeIf { it.destination.route == "channel?url={channelUrl}" }
        ?.arguments
        ?.getString("channelUrl")
        ?.let { encodedUrl ->
            runCatching { URLDecoder.decode(encodedUrl, Charsets.UTF_8.name()) }
                .getOrDefault(encodedUrl)
        }
        ?.let(::youtubeChannelUrl)

    if (currentUrl == targetUrl) return
    youtubeChannelRoute(targetUrl)?.let(::navigate)
}
