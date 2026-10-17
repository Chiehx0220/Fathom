package io.github.aedev.flow.bilibili

import java.net.URLDecoder
import java.net.URLEncoder

/** Uploader id ("mid"): all-digit string; a YouTube channel id never is. */
object BilibiliChannelId {
    private val SPACE_URL = Regex("""(?:space\.bilibili\.com|bilibili\.com/space)/(\d+)""")

    fun isMid(value: String): Boolean = value.isNotEmpty() && value.all(Char::isDigit)

    /** mid from bare number or space.bilibili.com link; else null. */
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

/** Series/season id: "bilibili:series:<mid>:<id>:<name>". Route-safe, names owning service; name included (series endpoint returns none). */
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

/** Live room id: "live:<roomId>". Shares video-id slots (routes, history, player); never a YouTube id. */
object BilibiliLiveId {
    private const val PREFIX = "live:"
    private val PATTERN = Regex("""^live:\d+$""")
    private val LINK = Regex("""^https?://live\.bilibili\.com/(?:[a-z][a-z0-9]*/)?(\d+)""", RegexOption.IGNORE_CASE)

    fun isLive(videoId: String): Boolean = PATTERN.matches(videoId)

    fun of(roomId: Long): String = "$PREFIX$roomId"

    /** Room number of a "live:<roomId>" id; else null. */
    fun roomIdOf(videoId: String): Long? = if (isLive(videoId)) videoId.removePrefix(PREFIX).toLongOrNull() else null

    /** Id of a live-room link; else null. */
    fun fromUrl(url: String): String? =
        LINK
            .find(url.trim())
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?.let(::of)

    fun toUrl(videoId: String): String = "https://live.bilibili.com/${videoId.removePrefix(PREFIX)}"
}

/** Video id: "BV1xx?p=2" (bvid + 1-based part). */
object BilibiliVideoId {
    private val PATTERN = Regex("""^BV[0-9A-Za-z]{10}(\?p=\d+)?$""")
    private val LINK = Regex("""/video/(BV[0-9A-Za-z]{10})""")
    private val PART = Regex("""[?&]p=(\d+)""")

    /** Bilibili video or live-room id. Id is the reliable test (saved service id may be stale); no YouTube id has "?" or ":". [BilibiliLiveId.isLive] separates rooms. */
    fun isBilibili(videoId: String): Boolean = PATTERN.matches(videoId) || BilibiliLiveId.isLive(videoId)

    /** "BV1xx?p=3" → ("BV1xx", 3); missing/invalid part = 1. */
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

    /** Id of a video link, share-tracking params dropped: ".../BV1xx/?p=2&spm=1" → "BV1xx?p=2"; no part → "BV1xx". Null if not a video link. */
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

    /** Canonical "BV1xx?p=N" id (storage/comparison form). */
    fun of(
        bvid: String,
        page: Int = 1,
    ): String = "$bvid?p=$page"

    /** Video link; live room link for "live:<roomId>". */
    fun toUrl(videoId: String): String =
        if (BilibiliLiveId.isLive(videoId)) BilibiliLiveId.toUrl(videoId) else "https://www.bilibili.com$VIDEO_PATH$videoId"

    private const val VIDEO_PATH = "/video/"
}

/** Any Bilibili link (video, uploader space). */
object BilibiliLink {
    private val HOST = Regex("""^https?://(?:[\w-]+\.)*bilibili\.com(?:[/?#:]|$)""", RegexOption.IGNORE_CASE)

    fun isBilibili(url: String): Boolean = HOST.containsMatchIn(url.trim())
}
