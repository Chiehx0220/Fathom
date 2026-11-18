package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.repository.DeArrowBrandingPolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

@Serializable
internal class DeArrowItemDto(
    val title: String? = null,
    val thumbnailUrl: String? = null,
)

/** `items` by video id, only where DeArrow replaces something; empty while off. */
@Serializable
internal class DeArrowDto(
    val enabled: Boolean,
    val items: Map<String, DeArrowItemDto>,
)

private const val MAX_VIDEOS = 60
private const val LOOKUP_TIMEOUT_MS = 4_000L
private val YOUTUBE_ID = Regex("[A-Za-z0-9_-]{11}")

/** `<id>:<seconds>,…` → YouTube ids, each once, capped; the duration places the random frame. */
internal fun parseDeArrowVideos(raw: String?): List<Pair<String, Int>> =
    raw
        .orEmpty()
        .split(',')
        .mapNotNull { entry ->
            entry.substringBefore(':').takeIf(YOUTUBE_ID::matches)?.let {
                it to
                    (entry.substringAfter(':', "").toIntOrNull() ?: 0)
            }
        }.distinctBy { it.first }
        .take(MAX_VIDEOS)

/** `ids` asks for branding; `enabled` switches DeArrow. */
internal fun RequestContext.handleApiDeArrow(params: Query): DeArrowDto {
    params.bool("enabled")?.let(dbHelper::setDeArrow)
    if (!dbHelper.deArrow()) return DeArrowDto(enabled = false, items = emptyMap())
    val wanted = parseDeArrowVideos(params["ids"])
    if (wanted.isEmpty()) return DeArrowDto(enabled = true, items = emptyMap())
    val repository = localServerEntryPoint(dbHelper.appContext).deArrow()
    val randomFrame = runBlocking { dbHelper.playerPreferences().deArrowRandomFrame.first() }
    val items =
        runBlocking {
            supervisorScope {
                wanted
                    .map { (id, duration) ->
                        async {
                            val content = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { repository.branding(id) } ?: return@async null
                            val result =
                                DeArrowBrandingPolicy.resolve(content, id, duration, randomFrame, isLiveOrUpcoming = false)
                                    ?: return@async null
                            val thumbnail =
                                result.thumbnailUrl?.let(::sameOriginImageUrl)
                                    ?: "https://i.ytimg.com/vi/$id/hq2.jpg".takeIf { result.youTubeFrames }
                            id to DeArrowItemDto(result.title, thumbnail)
                        }
                    }.awaitAll()
                    .filterNotNull()
                    .toMap()
            }
        }
    return DeArrowDto(enabled = true, items = items)
}
