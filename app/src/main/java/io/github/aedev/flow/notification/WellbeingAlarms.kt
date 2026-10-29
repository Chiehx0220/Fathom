package io.github.aedev.flow.notification

import android.content.Context
import io.github.aedev.flow.data.local.LocalDataManager
import kotlinx.coroutines.flow.first

/**
 * The bedtime and break reminders of the running profile. Alarms outlive the process, so a switch cancels them and
 * the next profile arms its own from its settings.
 */
internal object WellbeingAlarms {
    suspend fun arm(
        context: Context,
        settings: LocalDataManager,
    ) {
        if (settings.bedtimeReminder.first()) {
            ReminderManager.scheduleBedtimeReminder(context, settings.bedtimeStartHour.first(), settings.bedtimeStartMinute.first())
        } else {
            ReminderManager.cancelBedtimeReminder(context)
        }
        if (settings.breakReminder.first()) {
            ReminderManager.scheduleBreakReminder(context, settings.breakFrequency.first())
        } else {
            ReminderManager.cancelBreakReminder(context)
        }
    }

    fun cancel(context: Context) {
        ReminderManager.cancelBedtimeReminder(context)
        ReminderManager.cancelBreakReminder(context)
    }
}
