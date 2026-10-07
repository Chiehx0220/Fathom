package io.github.aedev.flow.bilibili

import io.github.aedev.flow.data.local.ThumbnailQuality

/*
 * Bilibili's image host (hdslb.com) resizes on request: "<cover url>@320w.webp" is the same cover at
 * 320 px wide, in the original aspect ratio. A cover is often a PNG of a megabyte or more, so the
 * thumbnail quality setting has to reach it the way it reaches YouTube's tiers.
 */

private val RESIZABLE_COVER = Regex("""^https?://[^/@]+\.hdslb\.com/bfs/[^@]+$""", RegexOption.IGNORE_CASE)

/**
 * [url] resized for [quality], best first and always ending in [url] itself, or null when [url] is not
 * a Bilibili cover this can resize (another host, or already carrying a size). HIGH is the original,
 * as it was before thumbnail quality was a choice. OFF fetches nothing, so nothing is resized either.
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
