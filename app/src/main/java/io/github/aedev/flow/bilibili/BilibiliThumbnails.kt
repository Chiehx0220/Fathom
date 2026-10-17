package io.github.aedev.flow.bilibili

import io.github.aedev.flow.data.local.ThumbnailQuality
import io.github.aedev.flow.data.localmedia.LocalMediaIds

/*
 * hdslb.com resizes on request: "<cover url>@320w.webp" = same cover, 320 px wide, aspect kept.
 * Covers are often multi-MB PNGs; thumbnail quality setting must apply like YouTube tiers.
 */

/** No `i.ytimg.com` thumbnail exists: device file, or Bilibili video (cover known only to Bilibili). */
fun hasNoYoutubeThumbnail(id: String): Boolean = LocalMediaIds.isLocal(id) || BilibiliVideoId.isBilibili(id)

private val RESIZABLE_COVER = Regex("""^https?://[^/@]+\.hdslb\.com/bfs/[^@]+$""", RegexOption.IGNORE_CASE)

/**
 * [url] resized for [quality], best first, ending in [url]; null if [url] is not a resizable Bilibili cover
 * (other host, or size already set). HIGH = original. OFF = no fetch, no resize.
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
