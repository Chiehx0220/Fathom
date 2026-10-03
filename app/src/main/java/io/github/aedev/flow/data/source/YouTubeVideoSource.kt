package io.github.aedev.flow.data.source

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.innertube.YouTubeSearchParams
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeVideoSource
    @Inject
    constructor(
        private val repository: YouTubeRepository,
    ) : VideoSource {
        override val serviceId: Int = ServiceList.YouTube.serviceId

        override suspend fun search(
            query: String,
            filter: SearchFilter,
        ): List<Video> = boundedOrEmpty(TIMEOUT_MS) { repository.searchVideos(query, params = paramsOf(filter)).first }

        override suspend fun related(videoId: String): List<Video> = boundedOrEmpty(TIMEOUT_MS) { repository.getRelatedCandidates(videoId) }

        private fun paramsOf(filter: SearchFilter): String? =
            when (filter) {
                SearchFilter.ANY -> {
                    null
                }

                SearchFilter.UPLOADED_THIS_WEEK -> {
                    YouTubeSearchParams.build(
                        contentType = YouTubeSearchParams.ContentType.VIDEO,
                        uploadDate = YouTubeSearchParams.UploadDate.THIS_WEEK,
                    )
                }

                SearchFilter.LIVE -> {
                    YouTubeSearchParams.build(
                        contentType = YouTubeSearchParams.ContentType.VIDEO,
                        features = setOf(YouTubeSearchParams.Feature.LIVE),
                    )
                }
            }

        private companion object {
            const val TIMEOUT_MS = 6_000L
        }
    }
