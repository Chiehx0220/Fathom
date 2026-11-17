package io.github.aedev.flow.localserver

import android.content.Context

internal interface ResumeStore {
    var armedAt: Long
}

/** Mark set before a profile switch; a restart inside [WINDOW_MS] brings the server back. */
internal class ServerResume(
    private val store: ResumeStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun arm() {
        store.armedAt = now()
    }

    fun disarm() {
        store.armedAt = 0L
    }

    /** True once for a recent mark; cleared either way. */
    fun consume(): Boolean {
        val at = store.armedAt
        store.armedAt = 0L
        return at > 0L && now() - at in 0..WINDOW_MS
    }

    companion object {
        const val WINDOW_MS = 2 * 60_000L

        private fun of(context: Context) =
            ServerResume(
                object : ResumeStore {
                    private val prefs = context.getSharedPreferences("fathom_server_resume", Context.MODE_PRIVATE)

                    // commit: process exits right after arming
                    override var armedAt: Long
                        get() = prefs.getLong("armed_at", 0L)
                        set(value) {
                            prefs.edit().putLong("armed_at", value).commit()
                        }
                },
            )

        fun arm(context: Context) = of(context).arm()

        fun disarm(context: Context) = of(context).disarm()

        fun consume(context: Context) = of(context).consume()
    }
}
