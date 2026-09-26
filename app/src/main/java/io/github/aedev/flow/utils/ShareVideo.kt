package io.github.aedev.flow.utils

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.aedev.flow.MainActivity
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

/** Shares a YouTube playlist's link, with its [title] as the subject. */
fun sharePlaylist(
    context: Context,
    playlistId: String,
    title: String,
) = shareLink(context, "https://www.youtube.com/playlist?list=$playlistId", title)

/** Shares [url] as plain text through the system sheet, with [title] as the subject. */
fun shareLink(
    context: Context,
    url: String,
    title: String,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
}

/**
 * Shares a playlist file, which another Flow imports when it is opened. Flow itself is left out of
 * the chooser so the sheet never offers to import the file back into the playlist it came from.
 */
fun sharePlaylistFile(
    context: Context,
    file: Uri,
    title: String,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = PLAYLIST_FILE_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, file)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(title, file)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser =
        Intent.createChooser(send, context.getString(R.string.share)).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
        }
    context.startActivity(chooser)
}

const val PLAYLIST_FILE_MIME_TYPE = "application/json"

fun shareVideo(
    context: Context,
    videoId: String,
    title: String,
    linkOnly: Boolean,
    serviceId: Int = ServiceList.YouTube.serviceId,
) {
    context.startActivity(shareVideoIntent(context, videoId, title, linkOnly, serviceId))
}
