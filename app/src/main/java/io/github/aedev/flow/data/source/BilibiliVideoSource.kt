package io.github.aedev.flow.data.source

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliLiveId
import io.github.aedev.flow.bilibili.BilibiliSearchItem
import io.github.aedev.flow.bilibili.BilibiliSearchType
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.BilibiliVideoMapper
import javax.inject.Inject
import javax.inject.Singleton

/** Bilibili's search has no time filter and no live filter, so both are done on what it returns. */
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
                    boundedOrEmpty(TIMEOUT_MS) { api.recommendedLives().map(BilibiliVideoMapper::videoFromLiveItem) }
                }

                SearchFilter.ANY -> {
                    searchVideos(query)
                }

                SearchFilter.UPLOADED_THIS_WEEK -> {
                    val since = System.currentTimeMillis() - WEEK_MS
                    searchVideos(query).filter { it.timestamp >= since }
                }
            }

        override suspend fun related(videoId: String): List<Video> =
            if (BilibiliLiveId.isLive(videoId)) {
                emptyList()
            } else {
                boundedOrEmpty(TIMEOUT_MS) {
                    api.related(BilibiliVideoId.parse(videoId).first).map(BilibiliVideoMapper::videoFromRelated)
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

        // The mapper stamps "now"; the upload time is what a freshness filter needs.
        private fun withUploadTime(item: BilibiliSearchItem.Video): Video {
            val video = BilibiliVideoMapper.videoFromSearch(item)
            return if (item.uploadTimeSec > 0) video.copy(timestamp = item.uploadTimeSec * MILLIS_PER_SECOND) else video
        }

        private companion object {
            const val TIMEOUT_MS = 6_000L
            const val MILLIS_PER_SECOND = 1000L
            const val WEEK_MS = 7L * 24L * 60L * 60L * 1000L
        }
    }
