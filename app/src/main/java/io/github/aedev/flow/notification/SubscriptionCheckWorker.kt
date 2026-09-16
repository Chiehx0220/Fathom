package io.github.aedev.flow.notification

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.innertube.RssSubscriptionService
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.isYouTubeServiceId
import io.github.aedev.flow.data.shorts.ChannelReelIndex
import io.github.aedev.flow.data.subscriptions.ChannelRssClient
import io.github.aedev.flow.data.subscriptions.ChannelRssParser
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that checks for new videos from subscribed channels using lightweight RSS
 * feeds, and seeds whatever it finds into the subscription feed cache.
 *
 * It shares [ChannelRssClient] with the in-app feed refresh, so both halves use one connection
 * pool, one timeout policy and one parser for the same endpoint.
 */
class SubscriptionCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    /**
     * WorkManager constructs this worker itself, so the dependencies are pulled from the singleton
     * graph here rather than injected. Using the graph keeps a single [SubscriptionFeedRepository]
     * and therefore a single refresh lock shared with the UI.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun subscriptionRepository(): SubscriptionRepository

        fun subscriptionFeedRepository(): SubscriptionFeedRepository

        fun rssSubscriptionService(): RssSubscriptionService

        fun channelRssClient(): ChannelRssClient

        fun channelReelIndex(): ChannelReelIndex

        fun playerPreferences(): PlayerPreferences
    }

    companion object {
        const val WORK_NAME = "subscription_check_work_v2"
        private const val LEGACY_WORK_NAME = "subscription_check_work"
        private const val IMMEDIATE_WORK_NAME = "subscription_check_work_now"
        private const val TAG = "SubscriptionCheckWorker"

        /** How many channels are polled concurrently. */
        private const val CHANNEL_CHUNK_SIZE = 10

        /**
         * A non-YouTube subscription (e.g. Bilibili) has no lightweight feed - every check is a full
         * channel-tabs fetch. Floored independently of the user's chosen overall interval (which is
         * tuned for YouTube's cheap RSS ping) so a short setting there can't turn into hammering
         * (and risking Bilibili's own risk-control blocking) every run instead.
         */
        private val NON_YOUTUBE_MIN_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(2)

        /**
         * Schedule periodic subscription checks
         * @param context Application context
         * @param intervalMinutes How often to check (default: 360 minutes / 6 hours)
         */
        suspend fun schedulePeriodicCheck(
            context: Context,
            intervalMinutes: Long = 360,
            reschedule: Boolean = false,
        ) {
            val notificationsEnabled = PlayerPreferences(context).notificationsEnabled.first()
            if (!notificationsEnabled) {
                cancelScheduledChecks(context)
                Log.d(TAG, "Skipping subscription check scheduling because notifications are disabled")
                return
            }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val workRequest =
                PeriodicWorkRequestBuilder<SubscriptionCheckWorker>(
                    intervalMinutes,
                    TimeUnit.MINUTES,
                ).setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ).build()

            WorkManager.getInstance(context).apply {
                cancelUniqueWork(LEGACY_WORK_NAME)
                enqueueUniquePeriodicWork(
                    WORK_NAME,
                    periodicWorkPolicy(reschedule),
                    workRequest,
                )
            }

            Log.d(TAG, "Scheduled periodic subscription check every $intervalMinutes minutes")
        }

        /**
         * Cancel scheduled subscription checks
         */
        fun cancelScheduledChecks(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(WORK_NAME)
                cancelUniqueWork(LEGACY_WORK_NAME)
            }
            Log.d(TAG, "Cancelled scheduled subscription checks")
        }

        /**
         * Run an immediate one-time check.
         */
        fun runImmediateCheck(context: Context) {
            val workRequest =
                OneTimeWorkRequestBuilder<SubscriptionCheckWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
            Log.d(TAG, "Started immediate subscription check")
        }
    }

    private val dependencies: Dependencies by lazy {
        EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting subscription check via RSS...")

            if (!dependencies.playerPreferences().notificationsEnabled.first()) {
                Log.d(TAG, "Notifications disabled, skipping subscription check")
                return@withContext Result.success()
            }

            try {
                val subscriptionRepository = dependencies.subscriptionRepository()
                val subscriptions =
                    subscriptionRepository
                        .getAllSubscriptions()
                        .first()
                        .filter { it.isNotificationEnabled }

                if (subscriptions.isEmpty()) {
                    Log.d(TAG, "No subscriptions with notifications enabled to check")
                    return@withContext Result.success()
                }

                Log.d(TAG, "Checking ${subscriptions.size} subscriptions")

                val announceReels = dependencies.playerPreferences().effectiveSubscriptionShowShorts.first()

                val newVideos = mutableListOf<NotificationHelper.NewVideoEntry>()
                subscriptions.chunked(CHANNEL_CHUNK_SIZE).forEach { chunk ->
                    coroutineScope {
                        chunk
                            .map { subscription ->
                                async {
                                    try {
                                        checkChannel(subscription, subscriptionRepository, announceReels)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error checking channel ${subscription.channelName}", e)
                                        emptyList()
                                    }
                                }
                            }.awaitAll()
                            .flatten()
                            .let { newVideos.addAll(it) }
                    }
                }

                if (newVideos.isNotEmpty()) {
                    NotificationHelper.showSubscriptionUpdates(applicationContext, newVideos)
                }

                Log.d(TAG, "Subscription check complete. Found ${newVideos.size} new videos.")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Error during subscription check", e)
                Result.retry()
            }
        }

    private suspend fun checkChannel(
        subscription: ChannelSubscription,
        repository: SubscriptionRepository,
        announceReels: Boolean,
    ): List<NotificationHelper.NewVideoEntry> {
        // The RSS client only speaks YouTube's feed format - a non-YouTube subscription (e.g.
        // Bilibili) has no such shortcut and goes through the heavier channel-tabs path instead,
        // floored to its own minimum interval below.
        if (!subscription.serviceId.isYouTubeServiceId) {
            return checkNonYouTubeChannel(subscription, repository, announceReels)
        }
        val feed =
            dependencies.channelRssClient().fetch(subscription.channelId).getOrElse { error ->
                Log.w(TAG, "Failed to check RSS for ${subscription.channelName}: ${error.message}")
                return emptyList()
            }

        val latestVideo = feed.entries.firstOrNull() ?: return emptyList()
        if (subscription.lastVideoId == latestVideo.videoId) return emptyList()

        val reelVideoIds = dependencies.channelReelIndex().reelIds(subscription.channelId).orEmpty()

        // The channel moved on, so hand the whole page to the feed cache. Rows land without a
        // duration — the feed's on-demand enrichment fills that in, and the next full refresh of
        // this channel replaces them outright. The reel verdict cannot wait that long: an unmarked
        // reel is visible in the feed in the meantime, Shorts switched off or not.
        dependencies.subscriptionFeedRepository().seedFromNotificationCheck(
            channelId = subscription.channelId,
            channelName = feed.channelName ?: subscription.channelName,
            entries = feed.entries,
            reelVideoIds = reelVideoIds,
        )

        val newEntries = ChannelRssParser.newEntriesSince(feed.entries, subscription.lastVideoId)
        repository.updateChannelLatestVideo(subscription.channelId, latestVideo.videoId)

        if (newEntries.isNotEmpty()) {
            Log.d(TAG, "${newEntries.size} new video(s) for ${subscription.channelName}")
        }

        return newEntries.filter { announceReels || it.videoId !in reelVideoIds }.map { video ->
            NotificationHelper.NewVideoEntry(
                channelName = subscription.channelName,
                videoTitle = video.title,
                videoId = video.videoId,
                thumbnailUrl = video.thumbnailUrl,
            )
        }
    }

    /**
     * A non-YouTube channel (e.g. Bilibili) has no RSS shortcut, so this is a full channel-tabs
     * fetch - floored to [NON_YOUTUBE_MIN_CHECK_INTERVAL_MS] regardless of the user's configured
     * overall interval, which is tuned for YouTube's much cheaper RSS ping.
     */
    private suspend fun checkNonYouTubeChannel(
        subscription: ChannelSubscription,
        repository: SubscriptionRepository,
        announceReels: Boolean,
    ): List<NotificationHelper.NewVideoEntry> {
        val now = System.currentTimeMillis()
        if (now - subscription.lastCheckTime < NON_YOUTUBE_MIN_CHECK_INTERVAL_MS) {
            return emptyList()
        }

        val videos =
            dependencies
                .rssSubscriptionService()
                .fetchLatestChannelVideos(subscription.channelId, subscription.serviceId)
        val latestVideo = videos.firstOrNull() ?: return emptyList()

        // Shorts have no Bilibili equivalent - nothing here needs a reel verdict, unlike the RSS
        // path above which shares an endpoint with actual YouTube Shorts.
        dependencies.subscriptionFeedRepository().seedFromNotificationCheck(videos)

        val newVideos = newVideosSince(videos, subscription.lastVideoId)
        repository.updateChannelLatestVideo(subscription.channelId, latestVideo.id)

        if (newVideos.isNotEmpty()) {
            Log.d(TAG, "${newVideos.size} new video(s) for ${subscription.channelName}")
        }

        return newVideos.map { video ->
            NotificationHelper.NewVideoEntry(
                channelName = subscription.channelName,
                videoTitle = video.title,
                videoId = video.id,
                thumbnailUrl = video.thumbnailUrl,
            )
        }
    }

    /** Mirrors [ChannelRssParser.newEntriesSince] for a plain [Video] list from the channel-tabs path. */
    private fun newVideosSince(
        videos: List<Video>,
        lastVideoId: String?,
    ): List<Video> {
        if (videos.isEmpty() || lastVideoId == null) return emptyList()
        val knownIndex = videos.indexOfFirst { it.id == lastVideoId }
        return when {
            knownIndex == 0 -> emptyList()
            knownIndex > 0 -> videos.take(minOf(knownIndex, ChannelRssParser.MAX_NEW_PER_CHANNEL))
            else -> videos.take(1)
        }
    }
}
