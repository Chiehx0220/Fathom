package io.github.aedev.flow.localserver

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** Return YouTube Dislike estimate; both counts null while off or unanswered. */
@Serializable
internal class DislikesDto(
    val enabled: Boolean,
    val dislikes: Long? = null,
    val likes: Long? = null,
)

private const val DISLIKES_TIMEOUT_MS = 5_000L

/** `id` (video url) asks for counts; `enabled` switches the service. */
internal fun RequestContext.handleApiDislikes(params: Query): DislikesDto {
    params.bool("enabled")?.let(dbHelper::setReturnYouTubeDislike)
    if (!dbHelper.returnYouTubeDislike()) return DislikesDto(enabled = false)
    val videoId = params["id"]?.let(LocalServerMedia::getVideoId).orEmpty()
    if (videoId.isEmpty()) return DislikesDto(enabled = true)
    val repository = localServerEntryPoint(dbHelper.appContext).youTubeRepository()
    val counts = runBlocking { withTimeoutOrNull(DISLIKES_TIMEOUT_MS) { repository.returnYouTubeDislikeCounts(videoId) } }
    return DislikesDto(enabled = true, dislikes = counts?.dislikes, likes = counts?.likes)
}
