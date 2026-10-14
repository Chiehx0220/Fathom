package io.github.aedev.flow.localserver

import java.util.concurrent.ConcurrentHashMap

internal const val CHOICE_HOLD_MS = 2500L

/** The things the phone can choose for the page, each shown on the phone at once. */
internal enum class RemoteChoice { QUALITY, RATE, DANMAKU }

/**
 * A choice made on the phone is shown at once, but the page's next report may still carry the old value (it was sent
 * before the command arrived). While a choice is held, that field of the report is ignored.
 */
internal class RemoteChoiceHold(
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val until = ConcurrentHashMap<RemoteChoice, Long>()

    fun hold(choice: RemoteChoice) {
        until[choice] = now() + CHOICE_HOLD_MS
    }

    private fun held(choice: RemoteChoice): Boolean = now() < (until[choice] ?: 0L)

    fun chosenQuality(
        reported: String?,
        shown: String,
    ): String = if (held(RemoteChoice.QUALITY)) shown else reported?.takeIf { it.isNotEmpty() } ?: "auto"

    fun rate(
        reported: String?,
        shown: Float,
    ): Float = if (held(RemoteChoice.RATE)) shown else reported?.toFloatOrNull()?.takeIf { it > 0f && it.isFinite() } ?: 1f

    /** Null when the page reports no danmaku, which a held choice does not override: a video without any has none to show. */
    fun danmaku(
        reported: String?,
        shown: Boolean?,
    ): Boolean? =
        when {
            reported == null -> null
            held(RemoteChoice.DANMAKU) -> shown
            else -> reported == "1"
        }
}
