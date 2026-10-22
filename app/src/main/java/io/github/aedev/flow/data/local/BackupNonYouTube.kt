package io.github.aedev.flow.data.local

import android.content.Context
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.data.model.isYouTubeServiceId
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.utils.resolveNonYouTubeChannelId
import io.github.aedev.flow.utils.resolveNonYouTubeChannelUrl
import io.github.aedev.flow.utils.resolveNonYouTubeStreamId
import io.github.aedev.flow.utils.videoIdToUrl
import kotlinx.coroutines.CancellationException

/*
 * Non-YouTube (Bilibili) carriage for BackupRepository imports/exports; upstream file only calls these.
 * Other services' ids/links go through the service link handler, not YouTube URL shapes.
 */

/** Video id of imported [url] for [serviceId]: YouTube via [youtube] (upstream parser), else link handler. */
internal fun importedVideoId(
    url: String,
    serviceId: Int,
    youtube: (String) -> String?,
): String? = if (serviceId.isYouTubeServiceId) youtube(url) else nonYouTubeVideoId(url, serviceId)

/** Channel id of imported uploader [url] for [serviceId]; blank if none. */
internal fun importedChannelId(
    url: String?,
    serviceId: Int,
    youtube: (String?) -> String,
): String =
    when {
        serviceId.isYouTubeServiceId -> youtube(url)
        url.isNullOrBlank() -> ""
        else -> resolveNonYouTubeChannelId(url, serviceId) { "" }
    }

/** Takeout history channel link: YouTube from `UC` id in [channelId], else service-specific. */
internal fun takeoutChannelUrl(
    channelId: String,
    serviceId: Int,
    ucIdRegex: Regex,
): String =
    if (serviceId.isYouTubeServiceId) {
        ucIdRegex
            .find(channelId.trim())
            ?.value
            ?.let { "https://www.youtube.com/channel/$it" }
            ?: "https://www.youtube.com/"
    } else {
        nonYouTubeChannelUrlOrHome(channelId, serviceId)
    }

/** Takeout history watch link. */
internal fun takeoutVideoUrl(
    videoId: String,
    serviceId: Int,
): String = videoIdToUrl(videoId, serviceId)

/** Video id of [url] for non-YouTube [serviceId]; null if not that service's. */
internal fun nonYouTubeVideoId(
    url: String,
    serviceId: Int,
): String? = resolveNonYouTubeStreamId(url, serviceId) { "" }.ifEmpty { null }

/** Non-YouTube channel link; YouTube home if id unknown to the service. */
internal fun nonYouTubeChannelUrlOrHome(
    channelId: String,
    serviceId: Int,
): String = resolveNonYouTubeChannelUrl(channelId, serviceId) { "" }.ifEmpty { "https://www.youtube.com/" }

/** Bilibili uploader from NewPipe export: name from export, avatar from Bilibili if it answers. */
internal suspend fun bilibiliSubscriptionFromNewPipe(
    context: Context,
    mid: Long,
    name: String,
): ChannelSubscription {
    val info =
        try {
            bilibiliApi(context).channelInfo(mid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    return ChannelSubscription(
        channelId = mid.toString(),
        channelName = name.ifBlank { info?.name.orEmpty() }.ifBlank { mid.toString() },
        channelThumbnail = info?.avatarUrl.orEmpty(),
        serviceId = BILIBILI_SERVICE_ID,
    )
}
