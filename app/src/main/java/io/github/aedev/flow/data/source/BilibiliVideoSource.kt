package io.github.aedev.flow.data.source

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.bilibili.BilibiliSearchItem
import io.github.aedev.flow.bilibili.BilibiliSearchType
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.BilibiliVideoMapper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/** No time/live filter in Bilibili search; both applied to results. */
@Singleton
class BilibiliVideoSource
    @Inject
    constructor(
        private val api: BilibiliApi,
    ) : VideoSource {
        override val serviceId: Int = BILIBILI_SERVICE_ID

        override suspend fun search(
            query: String,
            filter: SearchFilter,
        ): List<Video> =
            when (filter) {
                SearchFilter.LIVE -> {
                    lives()
                }

                SearchFilter.ANY -> {
                    searchVideos(query)
                }

                SearchFilter.UPLOADED_THIS_WEEK -> {
                    val since = System.currentTimeMillis() - WEEK_MS
                    searchVideos(query).filter { it.timestamp >= since }
                }
            }

        override fun queriesFor(
            queries: List<String>,
            filter: SearchFilter,
        ): List<String> = if (filter == SearchFilter.LIVE) listOf(queries.firstOrNull().orEmpty()) else queries.take(MAX_QUERIES)

        /** Popular list: [pages] fetched together; a failed page costs only its videos. */
        suspend fun popular(pages: Int = POPULAR_PAGES): List<Video> =
            coroutineScope {
                (1..pages)
                    .map { page -> async { boundedOrEmpty(TIMEOUT_MS) { api.popular(page) } } }
                    .awaitAll()
                    .flatten()
                    .distinctBy { it.bvid }
                    .map(BilibiliVideoMapper::videoFromRelated)
            }

        suspend fun lives(): List<Video> = boundedOrEmpty(TIMEOUT_MS) { api.recommendedLives().map(BilibiliVideoMapper::videoFromLiveItem) }

        override suspend fun related(videoId: String): List<Video> =
            if (BilibiliRef.Live.ofId(videoId) != null) {
                emptyList()
            } else {
                boundedOrEmpty(TIMEOUT_MS) {
                    api.related(BilibiliRef.Video.parse(videoId).bvid).map(BilibiliVideoMapper::videoFromRelated)
                }
            }

        private suspend fun searchVideos(query: String): List<Video> =
            boundedOrEmpty(TIMEOUT_MS) {
                api
                    .search(query, BilibiliSearchType.VIDEO, page = 1)
                    .items
                    .filterIsInstance<BilibiliSearchItem.Video>()
                    .map(::withUploadTime)
            }

        // Mapper stamps "now"; freshness filter needs upload time.
        private fun withUploadTime(item: BilibiliSearchItem.Video): Video {
            val video = BilibiliVideoMapper.videoFromSearch(item)
            return if (item.uploadTimeSec > 0) video.copy(timestamp = item.uploadTimeSec * MILLIS_PER_SECOND) else video
        }

        private companion object {
            const val TIMEOUT_MS = 6_000L
            const val MAX_QUERIES = 2
            const val POPULAR_PAGES = 3
            const val MILLIS_PER_SECOND = 1000L
            const val WEEK_MS = 7L * 24L * 60L * 60L * 1000L
        }
    }
