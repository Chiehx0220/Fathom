package io.github.aedev.flow.bilibili

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * How a Bilibili series or season is named inside Flow: "bilibili:series:<mid>:<id>:<name>". Flow's
 * playlist route and stores hold one string per playlist, and this one is safe in a route and says
 * which service to ask, which a bare number would not. The name rides along because the series
 * listing endpoint does not return one, and the playlist screen wants a title.
 */
object BilibiliPlaylistId {
    private const val PREFIX = "bilibili"

    data class Parsed(
        val kind: BilibiliPlaylistKind,
        val mid: Long,
        val id: Long,
        val name: String,
    )

    fun encode(
        kind: BilibiliPlaylistKind,
        mid: Long,
        id: Long,
        name: String,
    ): String = "$PREFIX:${kind.name.lowercase()}:$mid:$id:${URLEncoder.encode(name, "UTF-8")}"

    fun parse(raw: String): Parsed? {
        val parts = raw.split(':')
        if (parts.size != 5 || parts[0] != PREFIX) return null
        val kind = BilibiliPlaylistKind.entries.firstOrNull { it.name.equals(parts[1], ignoreCase = true) } ?: return null
        val mid = parts[2].toLongOrNull() ?: return null
        val id = parts[3].toLongOrNull() ?: return null
        val name = runCatching { URLDecoder.decode(parts[4], "UTF-8") }.getOrDefault("")
        return Parsed(kind, mid, id, name)
    }
}
