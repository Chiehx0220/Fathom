package io.github.aedev.flow.localserver.remote

import io.github.aedev.flow.localserver.RemoteSession

/** Sends a command to the paired page, showing the result of a switch or a choice on the phone at once; the page's next report confirms it. */
internal fun sendRemoteCommand(command: String) {
    val now = RemoteSession.remoteState.value
    val shown =
        when {
            command == "play_pause" -> {
                now.copy(paused = !now.paused)
            }

            command == "mute" -> {
                now.copy(muted = !now.muted)
            }

            command == "fullscreen" -> {
                now.copy(fullscreen = !now.fullscreen)
            }

            command.startsWith("quality:") -> {
                RemoteSession.choiceHold.holdQuality()
                now.copy(chosenQuality = command.removePrefix("quality:"))
            }

            command.startsWith("rate:") -> {
                command.removePrefix("rate:").toFloatOrNull()?.let { rate ->
                    RemoteSession.choiceHold.holdRate()
                    now.copy(rate = rate)
                }
            }

            command.startsWith("danmaku:") -> {
                RemoteSession.choiceHold.holdDanmaku()
                now.copy(danmaku = command.endsWith(":1"))
            }

            else -> {
                null
            }
        }
    shown?.let(RemoteSession::updateRemoteState)
    RemoteSession.addPendingCommand(command)
}
