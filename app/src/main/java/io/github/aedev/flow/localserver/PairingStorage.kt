package io.github.aedev.flow.localserver

import android.content.Context

/** Persisted pairing state: the code and the SHA-256 of each session token. */
internal data class PairingSnapshot(
    val code: String,
    val sessions: List<String>,
)

internal interface PairingStorage {
    fun load(): PairingSnapshot?

    fun save(snapshot: PairingSnapshot)
}

internal class PrefsPairingStorage(
    context: Context,
) : PairingStorage {
    private val prefs = context.getSharedPreferences("fathom_pairing", Context.MODE_PRIVATE)

    override fun load(): PairingSnapshot? {
        val code = prefs.getString(CODE, null) ?: return null
        val sessions =
            prefs
                .getString(SESSIONS, "")
                .orEmpty()
                .split(',')
                .filter { it.isNotEmpty() }
        return PairingSnapshot(code, sessions)
    }

    override fun save(snapshot: PairingSnapshot) {
        prefs
            .edit()
            .putString(CODE, snapshot.code)
            .putString(SESSIONS, snapshot.sessions.joinToString(","))
            .apply()
    }

    private companion object {
        const val CODE = "code"
        const val SESSIONS = "sessions"
    }
}
