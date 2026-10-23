package io.github.aedev.flow.localserver

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.core.app.NotificationCompat
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState

/** Delivers a [Handoff]: opens the video when the app is visible, else posts a notification. */
internal class HandoffDelivery(
    private val context: Context,
) {
    fun deliver(handoff: Handoff) {
        val open =
            Intent(Intent.ACTION_VIEW, Uri.parse(handoff.url))
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val visible = isAppVisible()
        seekLoadedVideo(handoff, play = visible)
        if (visible) context.startActivity(open) else notify(handoff, open)
    }

    // A loaded video is not reloaded by the player route, so history does not reseed it.
    private fun seekLoadedVideo(
        handoff: Handoff,
        play: Boolean,
    ) {
        val positionMs = handoff.positionMs ?: return
        if (GlobalPlayerState.currentVideo.value?.id != handoff.videoId) return
        EnhancedPlayerManager.getInstance().apply {
            seekTo(positionMs)
            if (play) play()
        }
    }

    private fun isAppVisible(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val importance = manager.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.importance
        return importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    private fun notify(
        handoff: Handoff,
        open: Intent,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.handoff_channel_name), NotificationManager.IMPORTANCE_HIGH),
        )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(handoff.title.ifEmpty { context.getString(R.string.handoff_channel_name) })
                .setContentText(context.getString(R.string.handoff_notification_text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        open,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setTimeoutAfter(TIMEOUT_MS)
                .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "flow_handoff"
        const val NOTIFICATION_ID = 4098
        const val TIMEOUT_MS = 10 * 60 * 1000L
    }
}
