package io.github.aedev.flow.ui.screens.home.chips

import io.github.aedev.flow.bilibili.BilibiliApi
import io.github.aedev.flow.bilibili.BilibiliSearchItem
import io.github.aedev.flow.bilibili.BilibiliSearchType
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.stream.BilibiliVideoMapper
import io.github.aedev.flow.ui.screens.home.fetchSafely
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

private const val BILIBILI_QUERY_LIMIT = 2
private const val MILLIS_PER_SECOND = 1000L

internal fun HomeContentSourceFilter.allows(serviceId: Int): Boolean = this.serviceId?.let { it == serviceId } ?: true

internal fun HomeContentSourceFilter.allows(video: Video): Boolean = allows(video.serviceId)

internal val HomeContentSourceFilter.wantsYouTube: Boolean get() = this != HomeContentSourceFilter.BILIBILI

internal val HomeContentSourceFilter.wantsBilibili: Boolean get() = this != HomeContentSourceFilter.YOUTUBE

// The mapper stamps "now"; Recently uploaded needs the upload time.
private fun withUploadTime(item: BilibiliSearchItem.Video): Video {
    val video = BilibiliVideoMapper.videoFromSearch(item)
    return if (item.uploadTimeSec > 0) video.copy(timestamp = item.uploadTimeSec * MILLIS_PER_SECOND) else video
}

/** Bounded Bilibili fetches for the chips; a failure yields no candidates. */
class BilibiliChipSource
    @Inject
    constructor(
        private val api: BilibiliApi,
    ) {
        suspend fun search(queries: List<String>): List<Video> =
            coroutineScope {
                queries
                    .take(BILIBILI_QUERY_LIMIT)
                    .map { query ->
                        async {
                            fetchSafely(timeoutMs = HomeChipParams.SEARCH_TIMEOUT_MS) {
                                api
                                    .search(query, BilibiliSearchType.VIDEO, page = 1)
                                    .items
                                    .filterIsInstance<BilibiliSearchItem.Video>()
                                    .map(::withUploadTime)
                            }
                        }
                    }.awaitAll()
                    .flatten()
            }

        suspend fun lives(): List<Video> =
            fetchSafely(timeoutMs = HomeChipParams.SEARCH_TIMEOUT_MS) {
                api.recommendedLives().map(BilibiliVideoMapper::videoFromLiveItem)
            }
    }
