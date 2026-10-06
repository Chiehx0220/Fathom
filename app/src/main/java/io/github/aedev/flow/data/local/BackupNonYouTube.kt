package io.github.aedev.flow.data.local

import android.content.Context
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.data.model.isYouTubeServiceId
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.resolveNonYouTubeChannelUrl
import io.github.aedev.flow.utils.resolveNonYouTubeStreamId
import kotlinx.coroutines.CancellationException

/*
 * What BackupRepository needs to carry a non-YouTube service (Bilibili) through an import or export, kept
 * here so that upstream file only calls these. Other services don't share YouTube's URL shapes, so their
 * ids and links go through the service's own link handler instead of guessing at URL structure.
 */

/** The video id in [url], a link of a non-YouTube [serviceId], or null when it is none of that service's. */
internal fun nonYouTubeVideoId(
    url: String,
    serviceId: Int,
): String? = resolveNonYouTubeStreamId(url, serviceId) { "" }.ifEmpty { null }

/** The channel link of a non-YouTube channel, or YouTube's home when the id is not one the service knows. */
internal fun nonYouTubeChannelUrlOrHome(
    channelId: String,
    serviceId: Int,
): String = resolveNonYouTubeChannelUrl(channelId, serviceId) { "" }.ifEmpty { "https://www.youtube.com/" }

/**
 * The thumbnail for an imported video that arrived without one. YouTube's follows from the id; another
 * service's cannot be guessed, and is left blank for the app to fill in when the video is first shown.
 */
internal fun fallbackThumbnail(
    videoId: String,
    serviceId: Int,
): String = if (serviceId.isYouTubeServiceId) ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId) else ""

/** A Bilibili uploader imported from a NewPipe export, named from the export and given its avatar by Bilibili when it answers. */
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
