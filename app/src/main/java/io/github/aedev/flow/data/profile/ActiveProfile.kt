package io.github.aedev.flow.data.profile

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

/**
 * The profile this process runs as. [attach] reads it as the first statement of `FlowApplication.attachBaseContext`,
 * before ContentProviders, WorkManager and Hilt can open a store, and it never changes afterwards: a switch restarts
 * the process. A process that never runs [attach], such as a restore under the system's own Application, reads it
 * on first use instead.
 */
object ActiveProfile {
    private const val TAG = "ActiveProfile"

    @Volatile
    private var registry: ProfileRegistry? = null

    @Volatile
    private var running: RunningProfile? = null

    fun attach(base: Context) {
        get(base)
    }

    fun get(context: Context): RunningProfile =
        running ?: synchronized(this) {
            running ?: read(context).also { running = it }
        }

    private fun read(context: Context): RunningProfile =
        ProfileRegistry.peek(registryFile(context)) ?: try {
            runBlocking { registry(context).running() }
        } catch (e: IOException) {
            Log.e(TAG, "Profile registry unreadable, running as Default", e)
            RunningProfile(id = "", isDefault = true)
        }

    internal fun registry(context: Context): ProfileRegistry =
        registry ?: synchronized(this) {
            registry ?: ProfileRegistry(registryFile(context)).also { registry = it }
        }

    // Not applicationContext: during attachBaseContext it is still null, so the path comes from the base context.
    private fun registryFile(context: Context) = File(context.filesDir, "datastore/${ProfileRegistry.FILE_NAME}")
}
