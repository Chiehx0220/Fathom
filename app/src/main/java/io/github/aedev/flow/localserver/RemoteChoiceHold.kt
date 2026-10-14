package io.github.aedev.flow.localserver

internal const val CHOICE_HOLD_MS = 2500L

/**
 * A quality or speed picked on the phone is shown at once, but the page's next report may still carry the old one
 * (it was sent before the command arrived). While a choice is held that field of the report is ignored.
 */
internal class RemoteChoiceHold(
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile private var qualityUntil = 0L

    @Volatile private var rateUntil = 0L

    fun holdQuality() {
        qualityUntil = now() + CHOICE_HOLD_MS
    }

    fun holdRate() {
        rateUntil = now() + CHOICE_HOLD_MS
    }

    fun chosenQuality(
        reported: String?,
        shown: String,
    ): String = if (now() < qualityUntil) shown else reported?.takeIf { it.isNotEmpty() } ?: "auto"

    fun rate(
        reported: String?,
        shown: Float,
    ): Float = if (now() < rateUntil) shown else reported?.toFloatOrNull()?.takeIf { it > 0f && it.isFinite() } ?: 1f
}
