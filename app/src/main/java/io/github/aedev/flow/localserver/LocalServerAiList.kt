package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.contentfilter.aislist.AiVerdict
import io.github.aedev.flow.data.contentfilter.aislist.AllowedAiChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

@Serializable
internal class AiAllowedDto(
    val id: String,
    val name: String,
)

/** AiSList: switches, list size, allowed channels, and `verdicts` (asked channel → `warn` | `block`; absent = not listed). */
@Serializable
internal class AiListDto(
    val block: Boolean,
    val warn: Boolean,
    val channelCount: Int? = null,
    val updatedAt: Long = 0,
    val allowed: List<AiAllowedDto> = emptyList(),
    val verdicts: Map<String, String> = emptyMap(),
)

private const val MAX_CHANNELS = 100
private const val STATUS_TIMEOUT_MS = 3_000L

internal fun AiVerdict.wireName(): String? =
    when (this) {
        AiVerdict.WARN -> "warn"
        AiVerdict.BLOCK -> "block"
        AiVerdict.NONE -> null
    }

/** `block`/`warn` switch the lists (the repository downloads on enable); `allow`+`name`/`disallow` edit the allowlist; `channels` asks for verdicts. */
internal fun RequestContext.handleApiAiList(params: Query): AiListDto {
    val repository = localServerEntryPoint(dbHelper.appContext).aiList()
    runBlocking {
        params.bool("block")?.let { repository.setBlockEnabled(it) }
        params.bool("warn")?.let { repository.setWarnEnabled(it) }
        params["allow"]?.let { url ->
            channelUrlToId(url)?.let { id ->
                repository.allow(AllowedAiChannel(id = id, name = params["name"].orEmpty(), handle = id.takeIf { it.startsWith('@') }))
            }
        }
        params["disallow"]?.let { url -> channelUrlToId(url)?.let { repository.disallow(it) } }
    }
    val matcher = repository.currentMatcher()
    val verdicts =
        params["channels"]
            .orEmpty()
            .split(',')
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_CHANNELS)
            .mapNotNull { channel -> channelUrlToId(channel)?.let { matcher.verdict(it, null).wireName() }?.let { channel to it } }
            .toMap()
    return runBlocking {
        val status = withTimeoutOrNull(STATUS_TIMEOUT_MS) { repository.status.first() }
        AiListDto(
            block = repository.blockEnabled.first(),
            warn = repository.warnEnabled.first(),
            channelCount = status?.channelCount,
            updatedAt = status?.updatedAtMs ?: 0,
            allowed = repository.allowedChannels.first().map { AiAllowedDto(channelIdToUrl(it.id), it.name) },
            verdicts = verdicts,
        )
    }
}
