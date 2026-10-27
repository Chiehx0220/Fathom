package io.github.aedev.flow.bilibili

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.R
import io.github.aedev.flow.localserver.videoIdToUrl

private const val BILIBILI_PACKAGE = "tv.danmaku.bili"

/** Bilibili app first; chooser excludes [MainActivity] (a bare link would loop back). */
fun openInBilibili(
    context: Context,
    videoId: String,
) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(videoIdToUrl(videoId)))
    try {
        context.startActivity(Intent(view).setPackage(BILIBILI_PACKAGE))
    } catch (_: ActivityNotFoundException) {
        val chooser =
            Intent.createChooser(view, context.getString(R.string.ui_open_in_service, "Bilibili")).apply {
                putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
            }
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.link_cannot_open), Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun openInSourceServiceLabel(isYouTube: Boolean): String =
    if (isYouTube) stringResource(R.string.ui_open_in_youtube) else stringResource(R.string.ui_open_in_service, "Bilibili")
