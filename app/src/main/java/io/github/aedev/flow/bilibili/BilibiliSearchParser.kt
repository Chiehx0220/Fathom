/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * services/bilibili/extractors/BilibiliStreamInfoItemExtractor.java (search video rows) and
 * BilibiliSearchResultChannelInfoItemExtractor.java (search user rows).
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

internal object BilibiliSearchParser {
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")

    fun toItem(item: SearchResponse.Item): BilibiliSearchItem? =
        when (item.type) {
            "video" -> video(item)
            "bili_user" -> user(item)
            else -> null
        }

    private fun video(item: SearchResponse.Item): BilibiliSearchItem.Video? {
        val bvid = BilibiliSigning.bvidOf(item.bvid, item.aid) ?: return null
        return BilibiliSearchItem.Video(
            bvid = bvid,
            title = cleanTitle(item.title),
            thumbnailUrl = absolute(item.pic),
            durationSec = parseDuration(item.duration),
            viewCount = item.play,
            uploader = BilibiliUploader(item.mid, item.author, absolute(item.upic)),
            uploadTimeSec = item.pubdate,
        )
    }

    private fun user(item: SearchResponse.Item): BilibiliSearchItem.User? {
        if (item.mid <= 0) return null
        return BilibiliSearchItem.User(
            mid = item.mid,
            name = item.uname,
            avatarUrl = absolute(item.upic),
            description = item.usign,
            followerCount = item.fans,
            videoCount = item.videos,
        )
    }

    /** Result images come as "//i0.hdslb.com/...". */
    private fun absolute(url: String): String = if (url.startsWith("//")) "https:$url" else url.toHttps()

    /** Matched words come wrapped in an em tag, and the text is HTML-escaped. */
    fun cleanTitle(raw: String): String = unescapeHtml(raw.replace("<em class=\"keyword\">", "").replace("</em>", ""))

    fun unescapeHtml(s: String): String {
        if (!s.contains('&')) return s
        val named =
            s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
        val numeric =
            NUMERIC_ENTITY.replace(named) { m ->
                val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
                if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
            }
        return numeric.replace("&amp;", "&")
    }

    /** "3:25" or "1:02:03" (seconds from the right); anything unreadable is 0. */
    fun parseDuration(text: String): Int {
        if (text.isBlank()) return 0
        return text.split(':').fold(0) { acc, part -> acc * 60 + (part.trim().toIntOrNull() ?: return 0) }
    }
}
