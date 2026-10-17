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

/** Singleton [BilibiliSession]: anonymous cookies, forged device, daily WBI key minted once, shared by all requests. */
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

/** Non-injected access to the singleton [BilibiliApi] (e.g. use case built with fixed arguments in tests). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface BilibiliEntryPoint {
    fun bilibiliApi(): BilibiliApi
}

fun bilibiliApi(context: Context): BilibiliApi =
    EntryPointAccessors.fromApplication(context.applicationContext, BilibiliEntryPoint::class.java).bilibiliApi()
