package io.github.aedev.flow.utils

import android.content.Context
import android.content.Intent
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.isYouTubeServiceId
import org.schabi.newpipe.extractor.ServiceList
import io.github.aedev.flow.localserver.videoIdToUrl

/**
 * The canonical watch link for a video, optionally seeked to [positionSeconds]. The timestamp
 * query param is a YouTube-only convention, so it is only appended for [ServiceList.YouTube].
 */
fun youtubeWatchUrl(
    videoId: String,
    positionSeconds: Long? = null,
    serviceId: Int = ServiceList.YouTube.serviceId,
): String {
    val watchUrl = videoIdToUrl(videoId, serviceId)
    return if (positionSeconds == null || !serviceId.isYouTubeServiceId) {
        watchUrl
    } else {
        "$watchUrl&t=${positionSeconds}s"
    }
}

/**
 * The chooser intent every "share this video" affordance raises. [linkOnly] is the user's
 * "share without text" preference: on, the payload is the bare link; off, it is the link under a
 * one-line introduction naming the video.
 */
fun shareVideoIntent(
    context: Context,
    videoId: String,
    title: String,
    linkOnly: Boolean,
    serviceId: Int = ServiceList.YouTube.serviceId,
): Intent {
    val url = youtubeWatchUrl(videoId, serviceId = serviceId)
    val shareText =
        if (linkOnly) {
            context.getString(R.string.share_link_only_template, url)
        } else {
            context.getString(R.string.check_out_video_template, title, url)
        }
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
    return Intent.createChooser(shareIntent, context.getString(R.string.share_video))
}

fun shareVideo(
    context: Context,
    videoId: String,
    title: String,
    linkOnly: Boolean,
    serviceId: Int = ServiceList.YouTube.serviceId,
) {
    context.startActivity(shareVideoIntent(context, videoId, title, linkOnly, serviceId))
}
