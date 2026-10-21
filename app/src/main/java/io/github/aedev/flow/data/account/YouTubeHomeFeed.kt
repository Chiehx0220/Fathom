package io.github.aedev.flow.data.account

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeHomeFeed
    @Inject
    constructor(
        private val store: GoogleAccountStore,
    ) {
        private val lock = Mutex()
        private var continuation: String? = null

        val active: Flow<Boolean> = store.account.map { it != null }.distinctUntilChanged()

        suspend fun isActive(): Boolean = store.account.first() != null

        val hasMore: Boolean
            get() = continuation != null

        suspend fun refresh(): Result<List<Video>> =
            lock.withLock {
                continuation = null
                page(null)
            }

        suspend fun loadMore(): Result<List<Video>> =
            lock.withLock {
                val token = continuation ?: return@withLock Result.success(emptyList())
                page(token)
            }

        private suspend fun page(token: String?): Result<List<Video>> =
            YouTube.homeFeed(token).map {
                continuation = it.continuation
                it.videos
            }
    }
