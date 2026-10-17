package io.github.aedev.flow.localserver

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom

private const val CODE_SPACE = 1_000_000
private const val CODE_DIGITS = 6
private const val MAX_SESSIONS = 20
private const val PEER_MISSES_BEFORE_LOCK = 5
private const val PEER_LOCK_MS = 10 * 60_000L
private const val MAX_TRACKED_PEERS = 256
private const val MISSES_BEFORE_NEW_CODE = 20

internal sealed interface PairResult {
    data class Paired(
        val token: String,
    ) : PairResult

    data object Wrong : PairResult

    data class Locked(
        val retryAfterSeconds: Long,
    ) : PairResult
}

/**
 * Six-digit code exchanged once for a session cookie. Guessing is bounded per peer ([PeerLockout]) and per code
 * ([MISSES_BEFORE_NEW_CODE]). Plain HTTP: code and cookie are observable on the network.
 */
internal class Pairing(
    private val storage: PairingStorage,
    private val random: SecureRandom = SecureRandom(),
    now: () -> Long = System::currentTimeMillis,
) {
    private val lockout = PeerLockout(PEER_MISSES_BEFORE_LOCK, PEER_LOCK_MS, MAX_TRACKED_PEERS, now)
    private val sessions = LinkedHashSet<String>()
    private var missesOnThisCode = 0
    private val currentCode = MutableStateFlow(newCode())

    val code: StateFlow<String> = currentCode

    init {
        storage.load()?.let { saved ->
            currentCode.value = saved.code
            sessions.addAll(saved.sessions)
        }
        save()
    }

    @Synchronized
    fun tryPair(
        peer: String,
        attempt: String,
    ): PairResult {
        val lockedMs = lockout.remainingMs(peer)
        if (lockedMs > 0) return PairResult.Locked((lockedMs + 999) / 1000)

        if (!MessageDigest.isEqual(attempt.toByteArray(), currentCode.value.toByteArray())) {
            lockout.recordMiss(peer)
            if (++missesOnThisCode >= MISSES_BEFORE_NEW_CODE) replaceCode()
            return PairResult.Wrong
        }
        lockout.clear(peer)
        return PairResult.Paired(issueSession())
    }

    @Synchronized
    fun isPaired(cookieHeader: String?): Boolean = SessionCookie.tokenIn(cookieHeader)?.let { hash(it) in sessions } ?: false

    /** Unpairs every browser. */
    @Synchronized
    fun reset() {
        sessions.clear()
        lockout.clear()
        replaceCode()
    }

    private fun issueSession(): String {
        val token = ByteArray(32).also(random::nextBytes).toHex()
        sessions.add(hash(token))
        while (sessions.size > MAX_SESSIONS) sessions.remove(sessions.first())
        save()
        return token
    }

    private fun replaceCode() {
        missesOnThisCode = 0
        currentCode.value = newCode()
        save()
    }

    private fun save() = storage.save(PairingSnapshot(currentCode.value, sessions.toList()))

    private fun newCode(): String = random.nextInt(CODE_SPACE).toString().padStart(CODE_DIGITS, '0')

    private fun hash(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        @Volatile
        private var instance: Pairing? = null

        fun of(context: Context): Pairing =
            instance ?: synchronized(this) {
                instance ?: Pairing(PrefsPairingStorage(context.applicationContext)).also { instance = it }
            }
    }
}
