package io.github.aedev.flow.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.dao.NotificationDao
import io.github.aedev.flow.data.local.dao.PlaylistDao
import io.github.aedev.flow.data.local.dao.VideoDao
import javax.inject.Qualifier
import javax.inject.Singleton

/** The running profile's database. With Default running it is the same instance as [DeviceDb]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ProfileDb

/** Default's database, which holds the download tables every profile shares. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceDb

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    @ProfileDb
    fun provideProfileDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = AppDatabase.profile(context)

    @Provides
    @Singleton
    @DeviceDb
    fun provideDeviceDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = AppDatabase.device(context)

    @Provides
    fun provideVideoDao(
        @ProfileDb database: AppDatabase,
    ): VideoDao = database.videoDao()

    @Provides
    fun providePlaylistDao(
        @ProfileDb database: AppDatabase,
    ): PlaylistDao = database.playlistDao()

    @Provides
    fun provideNotificationDao(
        @ProfileDb database: AppDatabase,
    ): NotificationDao = database.notificationDao()

    @Provides
    fun provideNoteDao(
        @ProfileDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.NoteDao = database.noteDao()

    @Provides
    fun provideCacheDao(
        @ProfileDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.CacheDao = database.cacheDao()

    @Provides
    fun provideHomeFeedCacheDao(
        @ProfileDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.HomeFeedCacheDao = database.homeFeedCacheDao()

    @Provides
    fun provideDownloadDao(
        @DeviceDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.DownloadDao = database.downloadDao()

    @Provides
    fun provideDownloadCollectionDao(
        @DeviceDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.DownloadCollectionDao = database.downloadCollectionDao()

    @Provides
    fun provideRecognitionHistoryDao(
        @ProfileDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.RecognitionHistoryDao = database.recognitionHistoryDao()

    @Provides
    fun provideSubscriptionGroupDao(
        @ProfileDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.SubscriptionGroupDao = database.subscriptionGroupDao()

    @Provides
    fun provideWatchHistoryDao(
        @ProfileDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.WatchHistoryDao = database.watchHistoryDao()

    @Provides
    fun provideSyncLogDao(
        @ProfileDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.SyncLogDao = database.syncLogDao()

    @Provides
    fun provideSyncPeerDao(
        @ProfileDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.SyncPeerDao = database.syncPeerDao()

    @Provides
    fun provideMusicGraphDao(
        @ProfileDb database: AppDatabase,
    ): io.github.aedev.flow.data.local.dao.MusicGraphDao = database.musicGraphDao()
}
