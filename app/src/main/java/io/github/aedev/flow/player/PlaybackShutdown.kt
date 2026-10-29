package io.github.aedev.flow.player

import android.app.ActivityManager
import android.content.Context
import io.github.aedev.flow.service.HeadphoneResume
import io.github.aedev.flow.service.MusicServiceState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ends both players the way closing them yourself does, so their services stop and their
 * notifications go. It holds no Activity, so it works however the timer was set and whether or not
 * a screen is still open.
 */
internal object PlaybackShutdown {
    private const val MUSIC_STOP_TIMEOUT_MS = 3_000L

    /** The sleep timer's Close app: also removes Flow's tasks. The process is left to Android: with nothing running it is only cached. */
    fun closeApp(context: Context) {
        stopPlayers(context)
        context.getSystemService(ActivityManager::class.java)?.appTasks?.forEach { it.finishAndRemoveTask() }
    }

    /**
     * Before a profile switch restarts the process: waits until the music service has stopped, so it saves its
     * listen session and is not restarted sticky into the next profile. Android Auto can keep it bound, hence the cap.
     */
    suspend fun stopForSwitch(context: Context) {
        stopVideo(context)
        EnhancedMusicPlayerManager.stopForProfileSwitch()
        withTimeoutOrNull(MUSIC_STOP_TIMEOUT_MS) { MusicServiceState.running.first { !it } }
    }

    private fun stopPlayers(context: Context) {
        stopVideo(context)
        EnhancedMusicPlayerManager.clearCurrentTrack()
    }

    private fun stopVideo(context: Context) {
        HeadphoneResume.set(context, allowed = false)
        EnhancedPlayerManager.getInstance().stopBackgroundService()
        GlobalPlayerState.release()
    }
}
