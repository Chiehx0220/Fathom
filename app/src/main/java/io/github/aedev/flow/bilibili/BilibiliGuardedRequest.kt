package io.github.aedev.flow.bilibili

import kotlinx.serialization.json.Json

/**
 * Up to two tries. An HTML page or code -352 means risk control, so the next try uses a fresh
 * device and cookies. The body is read whatever the HTTP status, since a block is a 412.
 *
 * @param referer the page the request claims to come from.
 * @param okCodes response codes that are an answer rather than a block (0, plus e.g. "comments closed").
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
