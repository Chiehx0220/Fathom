package io.github.aedev.flow.localserver.remote

import android.content.Context
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.localserver.RemoteSession
import io.github.aedev.flow.localserver.ServerService
import io.github.aedev.flow.localserver.videoIdToUrl
import io.github.aedev.flow.utils.YouTubeLink
import io.github.aedev.flow.utils.parseYouTubeLink

internal const val SETTINGS_ROUTE = "settings"

/** Watch URL of a video or short in [text]; else null. */
internal fun sharedVideoUrl(text: String?): String? =
    when (val link = text?.let(::parseYouTubeLink)) {
        is YouTubeLink.Video -> videoIdToUrl(link.id)
        is YouTubeLink.Short -> videoIdToUrl(link.id)
        else -> null
    }

/** True when delivered to a page; else held for the next page. */
internal fun queueVideoForPage(url: String) = RemoteSession.sendOrHold("play_video:$url")

/** Equivalent to the Settings switch. */
internal suspend fun enableLocalServer(context: Context) {
    PlayerPreferences(context).setLocalServerEnabled(true)
    ServerService.start(context)
}

internal fun webAddress() = "http://${ServerService.getLocalIpAddress() ?: "127.0.0.1"}:${ServerService.PORT}"
