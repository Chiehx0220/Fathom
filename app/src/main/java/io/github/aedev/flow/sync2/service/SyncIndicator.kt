package io.github.aedev.flow.sync2.service

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.notification.NotificationHelper
import io.github.aedev.flow.sync.transport.SyncForegroundService
import javax.inject.Inject
import javax.inject.Singleton

private const val NOTIFICATION_ID = 4097

/** The silent notification shown while a linked sync sends or receives data. */
@Singleton
class SyncIndicator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private fun notification(): Notification {
            SyncForegroundService.ensureChannel(context)
            return NotificationCompat
                .Builder(context, SyncForegroundService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_logo)
                .setContentTitle(context.getString(R.string.linked_syncing_notification))
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        /** For expedited work, which runs as a foreground service before Android 12. */
        fun foregroundInfo(): ForegroundInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(NOTIFICATION_ID, notification())
            }

        fun show() {
            if (NotificationHelper.hasNotificationPermission(
                    context,
                )
            ) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification())
            }
        }

        fun hide() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
