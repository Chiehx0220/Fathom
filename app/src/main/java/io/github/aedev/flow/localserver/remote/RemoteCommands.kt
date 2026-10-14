package io.github.aedev.flow.localserver.remote

import io.github.aedev.flow.localserver.RemoteChoice
import io.github.aedev.flow.localserver.RemoteSession

// The words of the protocol that the page's remote.js understands; keep them in step with its handle().
private const val QUALITY = "quality:"
private const val RATE = "rate:"
private const val DANMAKU = "danmaku:"
private const val VOLUME = "volume:"

internal fun qualityCommand(id: String) = QUALITY + id

internal fun rateCommand(rate: Float) = RATE + rate

internal fun danmakuCommand(on: Boolean) = DANMAKU + if (on) 1 else 0

internal fun volumeCommand(step: Float) = VOLUME + step

/** Sends a command to the paired page, showing the result of a switch or a choice on the phone at once; the page's next report confirms it. */
internal fun sendRemoteCommand(command: String) {
    val now = RemoteSession.remoteState.value
    val hold = RemoteSession.choiceHold
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

            command.startsWith(QUALITY) -> {
                hold.hold(RemoteChoice.QUALITY)
                now.copy(chosenQuality = command.removePrefix(QUALITY))
            }

            command.startsWith(RATE) -> {
                command.removePrefix(RATE).toFloatOrNull()?.let { rate ->
                    hold.hold(RemoteChoice.RATE)
                    now.copy(rate = rate)
                }
            }

            command.startsWith(DANMAKU) -> {
                hold.hold(RemoteChoice.DANMAKU)
                now.copy(danmaku = command == danmakuCommand(true))
            }

            else -> {
                null
            }
        }
    shown?.let(RemoteSession::updateRemoteState)
    RemoteSession.addPendingCommand(command)
}
