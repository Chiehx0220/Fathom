package io.github.aedev.flow.data.source

import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

enum class SearchFilter {
    ANY,
    UPLOADED_THIS_WEEK,
    LIVE,
}

/** One service's video catalogue. A call that times out or fails yields no videos. */
interface VideoSource {
    val serviceId: Int

    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.ANY,
    ): List<Video>

    suspend fun related(videoId: String): List<Video>
}

internal suspend fun <T> boundedOrEmpty(
    timeoutMs: Long,
    fetch: suspend () -> List<T>,
): List<T> =
    try {
        withTimeoutOrNull(timeoutMs) { fetch() }.orEmpty()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        emptyList()
    }
