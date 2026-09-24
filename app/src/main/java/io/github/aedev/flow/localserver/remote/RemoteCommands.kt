package io.github.aedev.flow.localserver.remote

import io.github.aedev.flow.localserver.LocalHttpServer

/**
 * Sends a command to the paired page. The toggles show their result on the phone at once; the page's next report
 * (it sends one right after every command) confirms or corrects it.
 */
internal fun sendRemoteCommand(command: String) {
    val now = LocalHttpServer.remoteState.value
    when (command) {
        "play_pause" -> LocalHttpServer.updateRemoteState(now.copy(paused = !now.paused))
        "mute" -> LocalHttpServer.updateRemoteState(now.copy(muted = !now.muted))
        "fullscreen" -> LocalHttpServer.updateRemoteState(now.copy(fullscreen = !now.fullscreen))
    }
    LocalHttpServer.addPendingCommand(command)
}
