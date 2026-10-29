package io.github.aedev.flow.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.profile.ActiveProfile
import io.github.aedev.flow.data.profile.ProfileWork
import java.util.concurrent.TimeUnit

class UpcomingVideoReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!ProfileWork.belongsHere(this)) return Result.success()
        val videoId = inputData.getString(KEY_VIDEO_ID).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val channelName = inputData.getString(KEY_CHANNEL_NAME).orEmpty()
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL_URL)

        if (videoId.isBlank() || title.isBlank() || channelName.isBlank()) {
            return Result.failure()
        }

        NotificationHelper.showUpcomingVideoLiveNotification(
            context = applicationContext,
            videoId = videoId,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
        )
        PlayerPreferences(applicationContext).setUpcomingVideoReminder(videoId, enabled = false)
        return Result.success()
    }

    companion object {
        private const val KEY_VIDEO_ID = "video_id"
        private const val KEY_TITLE = "title"
        private const val KEY_CHANNEL_NAME = "channel_name"
        private const val KEY_THUMBNAIL_URL = "thumbnail_url"

        fun scheduleReminder(
            context: Context,
            videoId: String,
            releaseTimeMs: Long,
            title: String,
            channelName: String,
            thumbnailUrl: String?,
        ) = enqueue(context, UpcomingReminder(videoId, releaseTimeMs, title, channelName, thumbnailUrl), ExistingWorkPolicy.REPLACE)

        /** Queues [reminder] again unless it is already queued, as at the start of its profile. */
        fun restore(
            context: Context,
            reminder: UpcomingReminder,
        ) = enqueue(context, reminder, ExistingWorkPolicy.KEEP)

        fun cancelReminder(
            context: Context,
            videoId: String,
        ) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(context, videoId))
        }

        private fun enqueue(
            context: Context,
            reminder: UpcomingReminder,
            policy: ExistingWorkPolicy,
        ) {
            val delayMs = (reminder.releaseTimeMs - System.currentTimeMillis()).coerceAtLeast(0L)
            val inputData =
                Data
                    .Builder()
                    .putString(KEY_VIDEO_ID, reminder.videoId)
                    .putString(KEY_TITLE, reminder.title)
                    .putString(KEY_CHANNEL_NAME, reminder.channelName)
                    .putString(KEY_THUMBNAIL_URL, reminder.thumbnailUrl)
                    .build()

            val request =
                OneTimeWorkRequestBuilder<UpcomingVideoReminderWorker>()
                    .setInputData(ProfileWork.data(context, inputData))
                    .addTag(ProfileWork.tag(context))
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(uniqueWorkName(context, reminder.videoId), policy, request)
        }

        // Default keeps the name reminders had before profiles; two profiles may remind the same premiere.
        private fun uniqueWorkName(
            context: Context,
            videoId: String,
        ): String {
            val profile = ActiveProfile.get(context)
            return if (profile.isDefault) "upcoming-video-reminder-$videoId" else "upcoming-video-reminder-${profile.id}-$videoId"
        }
    }
}
