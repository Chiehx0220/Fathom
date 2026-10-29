package io.github.aedev.flow.data.profile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.data.playlist.PLAYLIST_FILE_MAX_BYTES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * One link or playlist file that arrives while a switch is under way. The old profile doesn't open it; the restart
 * carries it to the new profile's first activity instead. A later arrival replaces an earlier one.
 */
object SwitchIntentQueue {
    private const val TAG = "SwitchIntentQueue"
    private const val EXTRA_HELD_PLAYLIST = "io.github.aedev.flow.extra.HELD_PLAYLIST"
    private const val HELD_DIR = "held_link"
    private const val HOLD_TIMEOUT_MS = 3_000L

    private val held = AtomicReference<Deferred<Intent?>?>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Holds [intent] when a switch is under way and returns true; [playlist] is the playlist file it opens, if any. */
    fun holdIfSwitching(
        context: Context,
        intent: Intent,
        playlist: Uri?,
    ): Boolean {
        if (!ProfileSwitchGate.isSwitching) return false
        val app = context.applicationContext
        val copy = Intent(intent)
        held.set(scope.async { if (playlist != null) copyPlaylist(app, playlist) else relaunchable(app, copy) })
        return true
    }

    /** What the restart opens: the held link, or the app as the launcher opens it. */
    internal suspend fun relaunchIntent(context: Context): Intent {
        val link = withTimeoutOrNull(HOLD_TIMEOUT_MS) { held.getAndSet(null)?.await() }
        return link?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java))
    }

    /** A switch that failed keeps its profile; a link held for the next one would open somewhere unexpected later. */
    internal fun drop() {
        held.getAndSet(null)?.cancel()
    }

    /** The playlist file a relaunch carries across a switch, if [intent] is one. */
    fun heldPlaylist(
        context: Context,
        intent: Intent,
    ): Uri? = heldFile(dir(context), intent.getStringExtra(EXTRA_HELD_PLAYLIST))?.let(Uri::fromFile)

    /** Only a file this queue wrote, named by its random token, so an outside intent can't point anywhere else. */
    internal fun heldFile(
        dir: File,
        token: String?,
    ): File? = token?.takeIf(::isSafeProfileId)?.let { File(dir, it) }?.takeIf(File::isFile)

    private fun relaunchable(
        context: Context,
        intent: Intent,
    ): Intent =
        intent.apply {
            if (component?.packageName != context.packageName) component = ComponentName(context, MainActivity::class.java)
        }

    // The read grant on a shared file ends with this process, so the file itself crosses the restart.
    private fun copyPlaylist(
        context: Context,
        source: Uri,
    ): Intent? {
        val dir = dir(context).apply { deleteRecursively() }
        dir.mkdirs()
        val target = File(dir, UUID.randomUUID().toString())
        val copied =
            runCatching { context.contentResolver.openInputStream(source)?.use { it.copyBoundedTo(target) } == true }
                .onFailure { Log.w(TAG, "Could not keep the playlist file for the next profile", it) }
                .getOrDefault(false)
        if (!copied) {
            target.delete()
            return null
        }
        return Intent(context, MainActivity::class.java).putExtra(EXTRA_HELD_PLAYLIST, target.name)
    }

    private fun InputStream.copyBoundedTo(target: File): Boolean =
        target.outputStream().use { out ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            var count = read(buffer)
            while (count >= 0) {
                total += count
                if (total > PLAYLIST_FILE_MAX_BYTES) return@use false
                out.write(buffer, 0, count)
                count = read(buffer)
            }
            true
        }

    private fun dir(context: Context) = File(context.cacheDir, HELD_DIR)
}
