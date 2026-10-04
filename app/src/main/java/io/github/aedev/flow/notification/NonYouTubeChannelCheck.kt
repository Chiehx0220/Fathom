package io.github.aedev.flow.notification

import android.content.Context
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.innertube.ChannelLabel
import io.github.aedev.flow.data.innertube.RssSubscriptionService
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.isYouTubeServiceId
import io.github.aedev.flow.data.subscriptions.ChannelRssParser
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import java.util.concurrent.TimeUnit

/**
 * The background check for a non-YouTube subscription (e.g. Bilibili), kept out of
 * [SubscriptionCheckWorker] so that upstream file only carries one call.
 *
 * The RSS client only speaks YouTube's feed format, so this path is a full channel-tabs fetch.
 * It is floored to [MIN_CHECK_INTERVAL_MS] regardless of the user's overall interval, which is tuned
 * for YouTube's cheap RSS ping; a short setting there must not hammer Bilibili into risk control.
 */
internal object NonYouTubeChannelCheck {
    private val MIN_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(2)
    private const val TAG = "NonYouTubeChannelCheck"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun rssSubscriptionService(): RssSubscriptionService

        fun subscriptionFeedRepository(): SubscriptionFeedRepository
    }

    /** Null when [subscription] is a YouTube channel, which the worker checks through RSS itself. */
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
                .rssSubscriptionService()
                .fetchLatestChannelVideos(
                    subscription.channelId,
                    label = ChannelLabel(subscription.channelName, subscription.channelThumbnail),
                )
        val latestVideo = videos.firstOrNull() ?: return emptyList()

        // Shorts have no Bilibili equivalent, so no reel verdict is needed here.
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
