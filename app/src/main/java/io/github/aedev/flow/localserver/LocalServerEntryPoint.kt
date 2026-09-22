package io.github.aedev.flow.localserver

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionWatchedVideos
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.ui.screens.home.HomeFeedSources
import okhttp3.OkHttpClient

/**
 * Local Server's HTTP handlers run outside Hilt's graph (a plain background thread pool, not an
 * injected component) - same situation as WidgetEntryPoint, SubscriptionFeedEntryPoint, and
 * SubscriptionCheckWorker.Dependencies elsewhere in this app. Reaches the SAME app-wide Hilt
 * singletons those use, instead of Local Server holding its own separate, unshared copies.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalServerEntryPoint {
    fun homeFeedSources(): HomeFeedSources

    fun subscriptionFeedRepository(): SubscriptionFeedRepository

    fun subscriptionWatchedVideos(): SubscriptionWatchedVideos

    fun videoDownloadManager(): VideoDownloadManager

    fun okHttpClient(): OkHttpClient
}

fun localServerEntryPoint(context: Context): LocalServerEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, LocalServerEntryPoint::class.java)
