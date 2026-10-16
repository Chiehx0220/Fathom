package io.github.aedev.flow.data.source

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.data.local.HomeContentSourceFilter
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject
import javax.inject.Singleton

/** Which service answers for a video, and which services a Home source filter asks. */
@Singleton
class VideoSources(
    private val sources: List<VideoSource>,
) {
    @Inject
    constructor(youtube: YouTubeVideoSource, bilibili: BilibiliVideoSource) : this(listOf(youtube, bilibili))

    fun forFilter(filter: HomeContentSourceFilter): List<VideoSource> = sources.filter { filter.allows(it.serviceId) }

    /** One discovery query asked of every service the filter allows; YouTube answers through [youtube]. */
    suspend fun search(
        query: String,
        filter: HomeContentSourceFilter,
        youtube: suspend (String) -> List<Video>,
    ): List<Video> =
        coroutineScope {
            forFilter(filter)
                .map { source ->
                    async { if (source.serviceId == ServiceList.YouTube.serviceId) youtube(query) else source.search(query) }
                }.awaitAll()
                .flatten()
        }

    fun forService(serviceId: Int): VideoSource? = sources.firstOrNull { it.serviceId == serviceId }

    /** A saved service id can be wrong; the id's own shape wins. */
    fun forVideo(
        videoId: String,
        savedServiceId: Int = ServiceList.YouTube.serviceId,
    ): VideoSource? = forService(serviceIdOfVideo(videoId, savedServiceId))

    fun forVideo(video: Video): VideoSource? = forVideo(video.id, video.serviceId)
}
