package io.github.aedev.flow.bilibili

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * An uploader is identified by a number (the "mid"). Flow's channel code often holds just that
 * string, and a YouTube channel is never all digits, so this is how the two are told apart.
 */
object BilibiliChannelId {
    private val SPACE_URL = Regex("""space\.bilibili\.com/(\d+)""")

    fun isMid(value: String): Boolean = value.isNotEmpty() && value.all(Char::isDigit)

    /** The mid from a bare number or a space.bilibili.com link; null for anything else. */
    fun midOf(value: String): Long? {
        val trimmed = value.trim()
        if (isMid(trimmed)) return trimmed.toLongOrNull()
        return SPACE_URL.find(trimmed)?.groupValues?.get(1)?.toLongOrNull()
    }
}

/**
 * How a series or season is named inside Flow: "bilibili:series:<mid>:<id>:<name>". Playlist routes
 * and stores hold one string per playlist; this one is safe in a route and says which service owns
 * it. The name is included because the series endpoint does not return one.
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
