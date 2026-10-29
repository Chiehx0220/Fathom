package io.github.aedev.flow.data.profile

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.io.File

/**
 * A seed or restore that waits in a profile's private folder until the profile opens. The start destination waits
 * for it, so a copied profile opens on Home with its settings instead of flashing onboarding.
 */
object PendingSlice {
    private const val TAG = "PendingSlice"
    const val FILE_NAME = "pending_restore.zip"
    private const val APPLYING_NAME = "pending_restore.applying"

    @Volatile
    private var applying: Deferred<Unit>? = null

    internal fun file(root: File) = File(root, FILE_NAME)

    /** Starts applying what waits in [root], if anything does. */
    internal fun start(
        root: File,
        scope: CoroutineScope,
        apply: suspend (File) -> Unit,
    ) {
        if (!file(root).exists() && !File(root, APPLYING_NAME).exists()) return
        applying =
            scope.async {
                val claimed = claim(root) ?: return@async
                try {
                    apply(claimed)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Could not apply the pending slice", e)
                } finally {
                    claimed.delete()
                }
            }
    }

    /** Returns once a pending slice, if one was started, has been applied or has failed. */
    suspend fun await() {
        runCatching { applying?.await() }.onFailure { Log.e(TAG, "Could not apply the pending slice", it) }
    }

    /**
     * Moves the waiting file aside before it is applied. A file already moved aside means a run died while applying
     * it, so it is dropped rather than retried into the same crash.
     */
    internal fun claim(root: File): File? {
        val claimed = File(root, APPLYING_NAME)
        if (claimed.exists()) {
            Log.w(TAG, "Dropping a pending slice that was being applied when the app stopped")
            claimed.delete()
        }
        return claimed.takeIf { file(root).renameTo(it) }
    }
}
