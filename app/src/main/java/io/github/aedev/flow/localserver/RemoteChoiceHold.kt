package io.github.aedev.flow.localserver

import java.util.concurrent.ConcurrentHashMap

internal const val CHOICE_HOLD_MS = 2500L

/** Phone-changed settings, shown optimistically. */
internal enum class RemoteChoice { QUALITY, RATE, DANMAKU, RATING }

/**
 * Optimistic-update guard: for [CHOICE_HOLD_MS] after a command, page report field ignored (in-flight report predates the command).
 */
internal class RemoteChoiceHold(
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val until = ConcurrentHashMap<RemoteChoice, Long>()

    fun hold(choice: RemoteChoice) {
        until[choice] = now() + CHOICE_HOLD_MS
    }

    private fun <T> resolve(
        choice: RemoteChoice,
        shown: T,
        reported: T,
    ): T = if (now() < (until[choice] ?: 0L)) shown else reported

    fun chosenQuality(
        reported: String?,
        shown: String,
    ): String = resolve(RemoteChoice.QUALITY, shown, reported?.takeIf { it.isNotEmpty() } ?: "auto")

    fun rate(
        reported: String?,
        shown: Float,
    ): Float = resolve(RemoteChoice.RATE, shown, reported?.toFloatOrNull()?.takeIf { it > 0f && it.isFinite() } ?: 1f)

    /** Null: page reports no danmaku (source has none). */
    fun danmaku(
        reported: String?,
        shown: Boolean?,
    ): Boolean? = reported?.let { resolve(RemoteChoice.DANMAKU, shown, it == "1") }

    /** Null: page reports no rating. */
    fun rating(
        reported: String?,
        shown: RemoteRating?,
    ): RemoteRating? =
        reported?.let {
            val value =
                when (it) {
                    "like" -> RemoteRating.LIKED
                    "dislike" -> RemoteRating.DISLIKED
                    else -> RemoteRating.NONE
                }
            resolve(RemoteChoice.RATING, shown, value)
        }
}
