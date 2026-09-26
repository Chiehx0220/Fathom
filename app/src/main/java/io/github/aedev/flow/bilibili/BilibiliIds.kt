package io.github.aedev.flow.bilibili

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * An uploader is identified by a number (the "mid"). Flow's channel code often holds just that
 * string, and a YouTube channel is never all digits, so this is how the two are told apart.
 */
object BilibiliChannelId {
    private val SPACE_URL = Regex("""(?:space\.bilibili\.com|bilibili\.com/space)/(\d+)""")

    fun isMid(value: String): Boolean = value.isNotEmpty() && value.all(Char::isDigit)

    /** The mid from a bare number or a space.bilibili.com link; null for anything else. */
    fun midOf(value: String): Long? {
        val trimmed = value.trim()
        if (isMid(trimmed)) return trimmed.toLongOrNull()
        return SPACE_URL
            .find(trimmed)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
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
        val parts = raw.split(':', limit = 5)
        if (parts.size != 5 || parts[0] != PREFIX) return null
        val kind = BilibiliPlaylistKind.entries.firstOrNull { it.name.equals(parts[1], ignoreCase = true) } ?: return null
        val mid = parts[2].toLongOrNull() ?: return null
        val id = parts[3].toLongOrNull() ?: return null
        val name = runCatching { URLDecoder.decode(parts[4], "UTF-8") }.getOrDefault("")
        return Parsed(kind, mid, id, name)
    }
}

/**
 * How a video is named inside Flow: "BV1xx?p=2", the bvid and the 1-based part. Everything that has
 * to read or build such an id does it here.
 */
object BilibiliVideoId {
    private val PATTERN = Regex("""^BV[0-9A-Za-z]{10}(\?p=\d+)?$""")
    private val LINK = Regex("""/video/(BV[0-9A-Za-z]{10})""")
    private val PART = Regex("""[?&]p=(\d+)""")

    /**
     * True for an id in that shape. Older saved rows can carry the wrong service id, so the id
     * itself is the surer test: no YouTube id looks like this (they are eleven characters and never
     * contain "?").
     */
    fun isBilibili(videoId: String): Boolean = PATTERN.matches(videoId)

    /** "BV1xx?p=3" -> ("BV1xx", 3). A missing or unreadable part number means the first part. */
    fun parse(videoId: String): Pair<String, Int> {
        val bvid = videoId.substringBefore('?')
        val page =
            videoId
                .substringAfter('?', "")
                .split('&')
                .firstOrNull { it.startsWith("p=") }
                ?.removePrefix("p=")
                ?.toIntOrNull()
                ?.takeIf { it >= 1 }
                ?: 1
        return bvid to page
    }

    /**
     * The id in a video link, ignoring share-tracking parameters: "https://www.bilibili.com/video/BV1xx/?p=2&spm=1"
     * -> "BV1xx?p=2", and "BV1xx" when the link names no part. Null when [url] is not a video link.
     */
    fun fromUrl(url: String): String? {
        val bvid = LINK.find(url)?.groupValues?.get(1) ?: return null
        val page =
            PART
                .find(url)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.takeIf { it >= 1 }
        return if (page != null) "$bvid?p=$page" else bvid
    }

    /** The id of one part in its canonical "BV1xx?p=N" form, which is how Flow stores and compares video ids. */
    fun of(
        bvid: String,
        page: Int = 1,
    ): String = "$bvid?p=$page"

    fun toUrl(videoId: String): String = "https://www.bilibili.com$VIDEO_PATH$videoId"

    private const val VIDEO_PATH = "/video/"
}

/** Whether a link points at Bilibili, of any kind (video, uploader space). */
object BilibiliLink {
    private val HOST = Regex("""^https?://(?:[\w-]+\.)*bilibili\.com(?:[/?#:]|$)""", RegexOption.IGNORE_CASE)

    fun isBilibili(url: String): Boolean = HOST.containsMatchIn(url.trim())
}
