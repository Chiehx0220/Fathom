package io.github.aedev.flow.ui.screens.home.chips

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.isYouTubeServiceId
import io.github.aedev.flow.data.source.SearchFilter
import io.github.aedev.flow.data.source.VideoSources
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

// Home source filter applied to chip feeds; HomeChipFeeds only calls these.

/** Queries per allowed service, shaped by each source. */
internal suspend fun VideoSources.searchAll(
    filter: HomeContentSourceFilter,
    queries: List<String>,
    searchFilter: SearchFilter,
): List<Video> =
    coroutineScope {
        forFilter(filter)
            .flatMap { source -> source.queriesFor(queries, searchFilter).map { query -> async { source.search(query, searchFilter) } } }
            .awaitAll()
            .flatten()
    }

/** [videos] of allowed services. */
internal suspend fun ChipFeedContext.inSource(videos: List<Video>): List<Video> =
    source().let { filter -> videos.filter { filter.allows(it) } }

/** [videoIds] of allowed services, by id shape. */
internal suspend fun ChipFeedContext.idsInSource(videoIds: List<String>): List<String> =
    source().let { filter -> videoIds.filter { filter.allows(serviceIdOfVideo(it)) } }

/** High-quality YouTube cover URL; other services keep theirs. */
internal fun Video.withHighQualityThumbnail(): Video =
    if (serviceId.isYouTubeServiceId) copy(thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(id)) else this
