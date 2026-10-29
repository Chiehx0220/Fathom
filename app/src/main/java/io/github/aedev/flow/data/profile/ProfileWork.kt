package io.github.aedev.flow.data.profile

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.await

/**
 * Background work that belongs to the profile that queued it. It carries the profile in its input and in a tag, so
 * a switch can cancel it first, and a run that still outlives a switch does nothing in the next profile.
 */
object ProfileWork {
    const val KEY_PROFILE_ID = "flow_profile_id"
    private const val TAG_PREFIX = "profile:"

    fun tag(context: Context): String = TAG_PREFIX + ActiveProfile.get(context).id

    fun data(
        context: Context,
        data: Data = Data.EMPTY,
    ): Data =
        Data
            .Builder()
            .putAll(data)
            .putString(KEY_PROFILE_ID, ActiveProfile.get(context).id)
            .build()

    /** False for work another profile queued; that work then ends without a side effect. */
    fun belongsHere(worker: ListenableWorker): Boolean =
        belongs(worker.inputData.getString(KEY_PROFILE_ID), ActiveProfile.get(worker.applicationContext))

    /** True when existing work with [tags] was queued by a profile other than the running one. */
    fun queuedByOther(
        context: Context,
        tags: Set<String>,
    ): Boolean = queuedByOther(tags, ActiveProfile.get(context))

    /** Cancels everything the running profile queued, and waits until WorkManager has recorded it. */
    suspend fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag(context)).await()
    }

    // Work queued before profiles existed carries no profile; Default was the only one then.
    internal fun belongs(
        stamped: String?,
        running: RunningProfile,
    ): Boolean = stamped?.let { it == running.id } ?: running.isDefault

    internal fun queuedByOther(
        tags: Set<String>,
        running: RunningProfile,
    ): Boolean = !belongs(tags.firstOrNull { it.startsWith(TAG_PREFIX) }?.removePrefix(TAG_PREFIX), running)
}
