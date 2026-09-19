/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * services/bilibili/extractors/BilibiliBulletCommentsExtractor.java (getInitialPage, VOD branch),
 * BilibiliBulletCommentsInfoItemExtractor.java, and utils.decompress.
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

enum class BilibiliDanmakuPosition { SCROLL, TOP, BOTTOM }

data class BilibiliDanmaku(
    val text: String,
    val timeMs: Long,
    val argbColor: Int,
    val position: BilibiliDanmakuPosition,
    val relativeFontSize: Float,
)

/** Parses the XML of `x/v1/dm/list.so`, the same way PipePipe's VOD bullet-comment extractor does. */
internal object BilibiliDanmakuParser {
    // PipePipe shifts every comment by 2.5s "for sync".
    private const val SYNC_OFFSET_MS = 2_500L
    private const val POOL_VOTING = 3

    private val ITEM = Regex("<d\\s+p=\"([^\"]*)\"[^>]*>(.*?)</d>", RegexOption.DOT_MATCHES_ALL)
    private val json = Json { isLenient = true }

    /**
     * The endpoint answers with raw deflate (no zlib header), which is what PipePipe inflates. A
     * response that is already plain XML, e.g. because a proxy decoded it, is passed through.
     */
    fun decodeBody(raw: ByteArray): String {
        if (raw.isEmpty()) return ""
        if (raw[0] == '<'.code.toByte()) return String(raw, Charsets.UTF_8)
        return inflate(raw, nowrap = true) ?: inflate(raw, nowrap = false) ?: String(raw, Charsets.UTF_8)
    }

    private fun inflate(
        data: ByteArray,
        nowrap: Boolean,
    ): String? {
        val inflater = Inflater(nowrap)
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 4)
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
            }
            if (out.size() == 0) null else out.toString(Charsets.UTF_8.name())
        } catch (e: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    fun parse(xml: String): List<BilibiliDanmaku> {
        if (xml.contains("<state>1</state>")) return emptyList()
        return ITEM.findAll(xml).mapNotNull { match -> toDanmaku(match.groupValues[1], match.groupValues[2]) }.toList()
    }

    private fun toDanmaku(
        p: String,
        body: String,
    ): BilibiliDanmaku? {
        val attr = p.split(',')
        if (attr.size < 6) return null
        if (attr[5].toIntOrNull() == POOL_VOTING) return null
        val seconds = attr[0].toDoubleOrNull() ?: return null
        val color = attr[3].toLongOrNull() ?: return null
        return BilibiliDanmaku(
            text = commentText(unescape(body)),
            timeMs = (seconds * 1000).toLong() + SYNC_OFFSET_MS,
            argbColor = (color + 0xFF000000L).toInt(),
            position =
                when (attr[1]) {
                    "4" -> BilibiliDanmakuPosition.BOTTOM
                    "5" -> BilibiliDanmakuPosition.TOP
                    else -> BilibiliDanmakuPosition.SCROLL
                },
            relativeFontSize =
                when (attr[2]) {
                    "18" -> 0.5f
                    "36" -> 0.7f
                    else -> 0.6f
                },
        )
    }

    /** Special (advanced) danmaku carry a JSON array whose 5th element is the text; others are plain. */
    private fun commentText(text: String): String {
        if (!text.startsWith("[")) return text
        return try {
            val array: JsonArray = json.parseToJsonElement(text).jsonArray
            array.getOrNull(4)?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: text
        } catch (e: Exception) {
            text
        }
    }

    private fun unescape(s: String): String =
        if (!s.contains('&')) {
            s
        } else {
            s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&")
        }
}
