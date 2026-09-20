package io.github.aedev.flow.ui

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import androidx.navigation.NavHostController
import io.github.aedev.flow.bilibili.BilibiliChannelId
import org.schabi.newpipe.extractor.ServiceList
import java.net.URLDecoder

/** Most channel clicks carry only an id. A bare number is a Bilibili uploader, never a YouTube channel. */
internal fun effectiveServiceId(
    channelIdOrHandle: String,
    serviceId: Int,
): Int =
    if (serviceId == ServiceList.YouTube.serviceId && BilibiliChannelId.isMid(channelIdOrHandle.trim())) {
        BILIBILI_SERVICE_ID
    } else {
        serviceId
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
