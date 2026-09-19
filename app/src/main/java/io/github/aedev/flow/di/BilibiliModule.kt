package io.github.aedev.flow.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliSession
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * One [BilibiliSession] for the whole app, so the anonymous cookie set, the forged device and the
 * daily WBI key are minted once and shared by every Bilibili request, the way a single browser
 * would present them.
 */
@Module
@InstallIn(SingletonComponent::class)
object BilibiliModule {
    @Provides
    @Singleton
    fun provideBilibiliSession(okHttpClient: OkHttpClient): BilibiliSession = BilibiliSession(okHttpClient)

    @Provides
    @Singleton
    fun provideBilibiliApi(session: BilibiliSession): BilibiliApi = BilibiliApi(session)
}

/**
 * For code that is not itself injected but is built by something that is (a use case constructed
 * with fixed arguments in tests, say): reaches the same singleton [BilibiliApi] without widening
 * that constructor.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BilibiliEntryPoint {
    fun bilibiliApi(): BilibiliApi
}

fun bilibiliApi(context: Context): BilibiliApi =
    EntryPointAccessors.fromApplication(context.applicationContext, BilibiliEntryPoint::class.java).bilibiliApi()
