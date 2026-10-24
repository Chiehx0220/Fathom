package io.github.aedev.flow.localserver.remote

import io.github.aedev.flow.localserver.RemoteChoice
import io.github.aedev.flow.localserver.RemoteRating
import io.github.aedev.flow.localserver.RemoteSession

// Command wire format; receiver: handle() in remote.js.
private const val QUALITY = "quality:"
private const val RATE = "rate:"
private const val DANMAKU = "danmaku:"
private const val VOLUME = "volume:"
internal const val LIKE = "like"
internal const val DISLIKE = "dislike"

internal fun qualityCommand(id: String) = QUALITY + id

internal fun rateCommand(rate: Float) = RATE + rate

internal fun danmakuCommand(on: Boolean) = DANMAKU + if (on) 1 else 0

internal fun volumeCommand(step: Float) = VOLUME + step

/** Queues [command] for paired page, applies expected result to local state; next report confirms. */
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

            command == LIKE || command == DISLIKE -> {
                hold.hold(RemoteChoice.RATING)
                val said = if (command == LIKE) RemoteRating.LIKED else RemoteRating.DISLIKED
                now.copy(rating = if (now.rating == said) RemoteRating.NONE else said)
            }

            else -> {
                null
            }
        }
    shown?.let(RemoteSession::updateRemoteState)
    RemoteSession.broadcast(command)
}
