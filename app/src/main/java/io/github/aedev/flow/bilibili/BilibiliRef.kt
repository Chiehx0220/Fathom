package io.github.aedev.flow.bilibili

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Typed identity, parsed once per boundary; string [id] only for storage/routes: `BV1xx?p=2`, `live:<roomId>`, `<mid>`,
 * `bilibili:series:<mid>:<id>:<name>`. Id shape decides ownership.
 */
sealed interface BilibiliRef {
    /** Storage/route form. */
    val id: String

    /** Web page (series: uploader space). */
    val url: String

    /** [partParam]: raw `?p=` or null; [part]: 1-based (absent/0/unreadable → 1). */
    data class Video(
        val bvid: String,
        val partParam: Int? = null,
    ) : BilibiliRef {
        val part: Int get() = partParam?.takeIf { it >= 1 } ?: 1
        override val id: String get() = if (partParam == null) bvid else "$bvid?p=$partParam"
        override val url: String get() = "https://www.bilibili.com/video/$id"

        companion object {
            private val LINK = Regex("""/video/(BV[0-9A-Za-z]{10})""")
            private val PART = Regex("""[?&]p=(\d+)""")

            /** Canonical "BV1xx?p=N". */
            fun of(
                bvid: String,
                part: Int = 1,
            ) = Video(bvid, part)

            /** Lenient parse of a known Bilibili id. */
            fun parse(raw: String): Video {
                val part =
                    raw
                        .substringAfter('?', "")
                        .split('&')
                        .firstOrNull { it.startsWith("p=") }
                        ?.removePrefix("p=")
                        ?.toIntOrNull()
                return Video(raw.substringBefore('?'), part)
            }

            /** Link → id, tracking params dropped. */
            fun ofUrl(url: String): Video? {
                val bvid = LINK.find(url)?.groupValues?.get(1) ?: return null
                val part =
                    PART
                        .find(url)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                        ?.takeIf { it >= 1 }
                return Video(bvid, part)
            }
        }
    }

    data class Live(
        val roomId: Long,
    ) : BilibiliRef {
        override val id: String get() = "$PREFIX$roomId"
        override val url: String get() = "https://live.bilibili.com/$roomId"

        companion object {
            private const val PREFIX = "live:"
            private val PATTERN = Regex("""^live:\d+$""")
            private val LINK = Regex("""^https?://live\.bilibili\.com/(?:[a-z][a-z0-9]*/)?(\d+)""", RegexOption.IGNORE_CASE)

            fun ofId(raw: String): Live? = if (PATTERN.matches(raw)) raw.removePrefix(PREFIX).toLongOrNull()?.let(::Live) else null

            fun ofUrl(url: String): Live? =
                LINK
                    .find(url.trim())
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
                    ?.let(::Live)
        }
    }

    data class Uploader(
        val mid: Long,
    ) : BilibiliRef {
        override val id: String get() = mid.toString()
        override val url: String get() = "https://space.bilibili.com/$mid"

        companion object {
            private val SPACE_URL = Regex("""(?:space\.bilibili\.com|bilibili\.com/space)/(\d+)""")

            /** All digits. */
            fun ofId(raw: String): Uploader? = if (raw.isNotEmpty() && raw.all(Char::isDigit)) raw.toLongOrNull()?.let(::Uploader) else null

            /** Bare mid or space link. */
            fun ofLink(raw: String): Uploader? {
                val trimmed = raw.trim()
                ofId(trimmed)?.let { return it }
                return SPACE_URL
                    .find(trimmed)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
                    ?.let(::Uploader)
            }
        }
    }

    /** Series/season; name in id (endpoint returns none), route-safe. */
    data class Series(
        val kind: BilibiliPlaylistKind,
        val mid: Long,
        val seriesId: Long,
        val name: String,
    ) : BilibiliRef {
        override val url: String get() = Uploader(mid).url
        override val id: String get() = "$PREFIX:${kind.name.lowercase()}:$mid:$seriesId:${URLEncoder.encode(name, "UTF-8")}"

        companion object {
            private const val PREFIX = "bilibili"

            fun ofId(raw: String): Series? {
                val parts = raw.split(':', limit = 5)
                if (parts.size != 5 || parts[0] != PREFIX) return null
                val kind = BilibiliPlaylistKind.entries.firstOrNull { it.name.equals(parts[1], ignoreCase = true) } ?: return null
                val mid = parts[2].toLongOrNull() ?: return null
                val seriesId = parts[3].toLongOrNull() ?: return null
                val name = runCatching { URLDecoder.decode(parts[4], "UTF-8") }.getOrDefault("")
                return Series(kind, mid, seriesId, name)
            }
        }
    }

    companion object {
        private val VIDEO_ID = Regex("""^BV[0-9A-Za-z]{10}(\?p=\d+)?$""")
        private val HOST = Regex("""^https?://(?:[\w-]+\.)*bilibili\.com(?:[/?#:]|$)""", RegexOption.IGNORE_CASE)

        /** Video or room id; null otherwise. */
        fun ofVideoId(raw: String): BilibiliRef? = if (VIDEO_ID.matches(raw)) Video.parse(raw) else Live.ofId(raw)

        fun isVideoId(raw: String): Boolean = ofVideoId(raw) != null

        /** Room, video or uploader link (bare mid ok). */
        fun ofLink(text: String): BilibiliRef? =
            Live.ofUrl(text) ?: Video.ofUrl(text)?.let { Video.of(it.bvid, it.part) } ?: Uploader.ofLink(text)

        /** Mid of bare mid or space link. */
        fun midOfLink(raw: String): Long? = Uploader.ofLink(raw)?.mid

        /** Any bilibili.com link. */
        fun isBilibiliUrl(url: String): Boolean = HOST.containsMatchIn(url.trim())
    }
}
