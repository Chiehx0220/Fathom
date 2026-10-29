package io.github.aedev.flow.data.profile

import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

enum class SwitchRefusal { UNKNOWN_PROFILE, ALREADY_ACTIVE, CAR_CONNECTED, SYNC_RUNNING, BACKUP_RUNNING }

sealed interface SwitchResult {
    data object Restarting : SwitchResult

    data class Refused(
        val reason: SwitchRefusal,
    ) : SwitchResult
}

/** What a switch does, in the order [ProfileSwitcher] runs it; the app's implementation is [AppProfileSwitchSteps]. */
interface ProfileSwitchSteps {
    suspend fun refusal(target: String): SwitchRefusal?

    fun closeGate(target: String)

    fun reopenGate()

    fun finishOpenSessions()

    suspend fun saveQueue()

    suspend fun stopPlayback()

    suspend fun awaitWrites()

    /** Keeps the leaving profile's data for the master backup, which reads a closed profile from it. */
    suspend fun writeSlice()

    /** Cancels what the running profile left scheduled or showing: its work, its alarms and its notifications. */
    suspend fun cancelProfileWork()

    /** Deletes caches that hold what the running profile searched for or shared. */
    suspend fun clearCaches()

    suspend fun persistActive(target: String)

    suspend fun restart()
}

/**
 * Switches profile by restarting the process, so nothing held in memory can carry over. The new profile is only
 * persisted once playback has stopped and the old profile's writes are on disk, and the restart comes last: from
 * there on, anything the system restarts comes up as the new profile.
 */
@Singleton
class ProfileSwitcher internal constructor(
    private val steps: ProfileSwitchSteps,
    private val writeTimeoutMs: Long,
) {
    @Inject
    constructor(steps: AppProfileSwitchSteps) : this(steps, WRITE_TIMEOUT_MS)

    suspend fun switchTo(target: String): SwitchResult {
        steps.refusal(target)?.let { return SwitchResult.Refused(it) }
        steps.closeGate(target)
        try {
            steps.finishOpenSessions()
            steps.saveQueue()
            steps.stopPlayback()
            // A write that never finishes must not keep the user from switching; what landed is kept.
            withTimeoutOrNull(writeTimeoutMs) { steps.awaitWrites() }
            // Bounded like the writes: a slow export leaves this profile out of the next master backup, never stuck.
            withTimeoutOrNull(SLICE_TIMEOUT_MS) { steps.writeSlice() }
            // Cancelled before the new profile is persisted: WorkManager reruns interrupted work in the next process.
            steps.cancelProfileWork()
            steps.clearCaches()
            steps.persistActive(target)
            steps.restart()
        } catch (e: Throwable) {
            steps.reopenGate()
            throw e
        }
        return SwitchResult.Restarting
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 5_000L
    }
}

private const val SLICE_TIMEOUT_MS = 15_000L
