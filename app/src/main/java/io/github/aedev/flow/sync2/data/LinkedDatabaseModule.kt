package io.github.aedev.flow.sync2.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.profile.ProfileFiles
import io.github.aedev.flow.data.profile.StoreScope
import io.github.aedev.flow.sync2.store.LocalStore
import javax.inject.Singleton

/** One file, in Default's databases folder: only Default syncs, so no other profile opens it. */
@Module
@InstallIn(SingletonComponent::class)
object LinkedDatabaseModule {
    @Provides
    @Singleton
    fun provideLinkedDatabase(
        @ApplicationContext context: Context,
    ): LinkedDatabase = Room.databaseBuilder(context, LinkedDatabase::class.java, "linked_devices").build()

    @Provides
    @Singleton
    fun provideLocalStore(
        @ApplicationContext context: Context,
        db: LinkedDatabase,
    ): LocalStore = LocalStore(ProfileFiles.file(context, StoreScope.DEFAULT_ONLY, "linked_store"), db.held())
}
