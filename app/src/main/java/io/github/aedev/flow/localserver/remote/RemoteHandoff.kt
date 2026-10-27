package io.github.aedev.flow.localserver.remote

import io.github.aedev.flow.localserver.RemoteSession
import io.github.aedev.flow.localserver.videoIdToUrl
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import java.util.Locale

private const val PLAY_VIDEO_AT = "play_video_at:"

/** Wire format `play_video_at:<seconds>:<url>`; receiver: handle() in remote.js. */
internal fun playVideoAtCommand(
    url: String,
    positionMs: Long,
) = PLAY_VIDEO_AT + String.format(Locale.US, "%.1f", positionMs.coerceAtLeast(0L) / 1000.0) + ":" + url

/** Sends the playing video to the paired page at current position; pauses the phone. */
internal fun sendPhoneVideoToPage(): Boolean {
    val video = GlobalPlayerState.currentVideo.value ?: return false
    val player = EnhancedPlayerManager.getInstance()
    RemoteSession.sendOrHold(playVideoAtCommand(videoIdToUrl(video.id, video.serviceId), player.getCurrentPosition()))
    player.pause()
    return true
}
