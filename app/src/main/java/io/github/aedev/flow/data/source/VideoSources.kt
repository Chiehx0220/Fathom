package io.github.aedev.flow.data.source

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject
import javax.inject.Singleton

/** Per-video service dispatch; services asked per Home source filter. */
@Singleton
class VideoSources(
    private val sources: List<VideoSource>,
    private val homeFilter: suspend () -> HomeContentSourceFilter = { HomeContentSourceFilter.MIX },
) {
    @Inject
    constructor(youtube: YouTubeVideoSource, bilibili: BilibiliVideoSource, preferences: PlayerPreferences) :
        this(listOf(youtube, bilibili), { preferences.homeContentSourceFilter.first() })

    fun forFilter(filter: HomeContentSourceFilter): List<VideoSource> = sources.filter { filter.allows(it.serviceId) }

    /** One discovery query to every service the Home filter allows; YouTube via [youtube]. */
    suspend fun search(
        query: String,
        youtube: suspend (String) -> List<Video>,
    ): List<Video> =
        coroutineScope {
            forFilter(homeFilter())
                .map { source ->
                    async { if (source.serviceId == ServiceList.YouTube.serviceId) youtube(query) else source.search(query) }
                }.awaitAll()
                .flatten()
        }

    fun forService(serviceId: Int): VideoSource? = sources.firstOrNull { it.serviceId == serviceId }

    /** Saved service id may be wrong; id shape wins. */
    fun forVideo(
        videoId: String,
        savedServiceId: Int = ServiceList.YouTube.serviceId,
    ): VideoSource? = forService(serviceIdOfVideo(videoId, savedServiceId))

    fun forVideo(video: Video): VideoSource? = forVideo(video.id, video.serviceId)
}
