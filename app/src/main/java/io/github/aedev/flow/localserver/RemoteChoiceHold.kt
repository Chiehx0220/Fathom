package io.github.aedev.flow.localserver

import java.util.concurrent.ConcurrentHashMap

internal const val CHOICE_HOLD_MS = 2500L

/** Settings that the phone changes and shows optimistically. */
internal enum class RemoteChoice { QUALITY, RATE, DANMAKU, RATING }

/**
 * Optimistic-update guard: for [CHOICE_HOLD_MS] after a command, the matching field of the page's report is ignored,
 * since a report already in flight predates the command.
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

    /** Null when the page does not report danmaku (the source has none). */
    fun danmaku(
        reported: String?,
        shown: Boolean?,
    ): Boolean? = reported?.let { resolve(RemoteChoice.DANMAKU, shown, it == "1") }

    /** Null when the page does not report a rating. */
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
