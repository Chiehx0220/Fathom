package io.github.aedev.flow.notification

import android.content.Context
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.innertube.ChannelLabel
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.isYouTubeServiceId
import io.github.aedev.flow.data.subscriptions.BilibiliSubscriptionFeed
import io.github.aedev.flow.data.subscriptions.ChannelRssParser
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import java.util.concurrent.TimeUnit

/**
 * Background check of a non-YouTube subscription (e.g. Bilibili); [SubscriptionCheckWorker] only calls it.
 *
 * RSS client is YouTube-only: full channel-tabs fetch here. Floored to [MIN_CHECK_INTERVAL_MS] regardless of user interval
 * (tuned for cheap YouTube RSS ping; short interval would hammer Bilibili into risk control).
 */
internal object NonYouTubeChannelCheck {
    private val MIN_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(2)
    private const val TAG = "NonYouTubeChannelCheck"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun bilibiliSubscriptionFeed(): BilibiliSubscriptionFeed

        fun subscriptionFeedRepository(): SubscriptionFeedRepository
    }

    /** Null for a YouTube channel (worker checks via RSS). */
    suspend fun checkOrNull(
        context: Context,
        subscription: ChannelSubscription,
        repository: SubscriptionRepository,
    ): List<NotificationHelper.NewVideoEntry>? {
        if (subscription.serviceId.isYouTubeServiceId) return null
        return check(EntryPointAccessors.fromApplication(context, Dependencies::class.java), subscription, repository)
    }

    private suspend fun check(
        dependencies: Dependencies,
        subscription: ChannelSubscription,
        repository: SubscriptionRepository,
    ): List<NotificationHelper.NewVideoEntry> {
        val now = System.currentTimeMillis()
        if (now - subscription.lastCheckTime < MIN_CHECK_INTERVAL_MS) {
            return emptyList()
        }

        val videos =
            dependencies
                .bilibiliSubscriptionFeed()
                .latestVideos(
                    subscription.channelId,
                    label = ChannelLabel(subscription.channelName, subscription.channelThumbnail),
                )
        val latestVideo = videos.firstOrNull() ?: return emptyList()

        // No Bilibili shorts: no reel verdict.
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

    /** As [ChannelRssParser.newEntriesSince] for a plain [Video] list from channel-tabs. */
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
