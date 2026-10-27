package io.github.aedev.flow.bilibili

import io.github.aedev.flow.data.local.ThumbnailQuality
import io.github.aedev.flow.data.localmedia.LocalMediaIds

// hdslb.com resizes on request ("<cover>@320w.webp"): thumbnail quality setting applies like YouTube tiers.

/** No `i.ytimg.com` thumbnail exists: device file, or Bilibili video (cover known only to Bilibili). */
fun hasNoYoutubeThumbnail(id: String): Boolean = LocalMediaIds.isLocal(id) || BilibiliRef.isVideoId(id)

private val RESIZABLE_COVER = Regex("""^https?://[^/@]+\.hdslb\.com/bfs/[^@]+$""", RegexOption.IGNORE_CASE)

/**
 * [url] resized for [quality], best first, ending in [url]; null if not a resizable cover. HIGH = original; OFF = no fetch.
 */
fun bilibiliThumbnailCandidates(
    url: String,
    quality: ThumbnailQuality,
    portrait: Boolean,
): List<String>? {
    if (!RESIZABLE_COVER.matches(url)) return null
    val width =
        when (quality) {
            ThumbnailQuality.HIGH -> return listOf(url)
            ThumbnailQuality.MEDIUM -> MEDIUM_WIDTH
            ThumbnailQuality.LOW -> if (portrait) PORTRAIT_LOW_WIDTH else LOW_WIDTH
            ThumbnailQuality.OFF -> return null
        }
    return listOf("$url@${width}w.webp", url)
}

private const val MEDIUM_WIDTH = 672
private const val PORTRAIT_LOW_WIDTH = 480
private const val LOW_WIDTH = 320
