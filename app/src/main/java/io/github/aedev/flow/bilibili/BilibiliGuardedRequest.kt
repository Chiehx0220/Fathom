package io.github.aedev.flow.bilibili

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Up to 2 tries; HTML or code -352 = risk control, retry with fresh device and cookies.
 * Body read regardless of HTTP status (block = 412).
 *
 * @param referer claimed origin page.
 * @param okCodes codes that are answers, not blocks (0, "comments closed").
 */
internal suspend fun BilibiliSession.guardedGet(
    json: Json,
    referer: String,
    okCodes: Set<Int> = setOf(0),
    url: suspend () -> String,
): String {
    var last = ""
    repeat(2) {
        last = getLenient(url(), headers(referer))
        if (!last.trimStart().startsWith("{")) {
            reset()
            return@repeat
        }
        val code = runCatching { json.decodeFromString<CodeOnly>(last).code }.getOrDefault(-1)
        if (code in okCodes) return last
        if (code == -352) reset()
    }
    throw BilibiliContentNotAvailableException("Bilibili blocked the request: ${last.take(120)}")
}

/** Unreadable body → content unavailable. */
internal inline fun <reified T> Json.decodeBilibili(
    body: String,
    what: String = "response",
): T =
    try {
        decodeFromString<T>(body)
    } catch (e: SerializationException) {
        throw BilibiliContentNotAvailableException("Unreadable Bilibili $what: ${e.message}")
    }

@Serializable
internal data class CodeOnly(
    val code: Int = 0,
    val message: String = "",
)
