package io.github.aedev.flow.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LocalDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Posts a wellbeing reminder while it is still on in the running profile; the break reminder then arms the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val type = intent.getStringExtra("type") ?: return
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                remind(app, type, LocalDataManager(app))
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun remind(
        context: Context,
        type: String,
        settings: LocalDataManager,
    ) {
        when (type) {
            "bedtime" -> {
                if (!settings.bedtimeReminder.first()) return
                NotificationHelper.showReminderNotification(
                    context,
                    context.getString(R.string.reminder_bedtime_title),
                    context.getString(R.string.reminder_bedtime_body),
                )
            }

            "break" -> {
                if (!settings.breakReminder.first()) return
                NotificationHelper.showReminderNotification(
                    context,
                    context.getString(R.string.reminder_break_title),
                    context.getString(R.string.reminder_break_body),
                )
                ReminderManager.scheduleBreakReminder(context, settings.breakFrequency.first())
            }
        }
    }
}
