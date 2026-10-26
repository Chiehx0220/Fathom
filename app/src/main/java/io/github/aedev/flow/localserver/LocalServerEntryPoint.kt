package io.github.aedev.flow.localserver

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.repository.YouTubePlaylistRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionWatchedVideos
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.data.video.downloader.work.DownloadController
import io.github.aedev.flow.ui.screens.home.HomeFeedSources
import okhttp3.OkHttpClient

/** HTTP handlers run outside Hilt (plain thread pool), like WidgetEntryPoint; reaches the same app-wide singletons. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalServerEntryPoint {
    fun homeFeedSources(): HomeFeedSources

    fun subscriptionFeedRepository(): SubscriptionFeedRepository

    fun subscriptionWatchedVideos(): SubscriptionWatchedVideos

    fun videoDownloadManager(): VideoDownloadManager

    fun downloadController(): DownloadController

    fun okHttpClient(): OkHttpClient

    fun youTubePlaylistRepository(): YouTubePlaylistRepository
}

fun localServerEntryPoint(context: Context): LocalServerEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, LocalServerEntryPoint::class.java)
