package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.renderer.lockupDateAndViews
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal data class WebHomePage(
    val videos: List<Video>,
    val continuation: String?,
)

private const val VIDEO_LOCKUP = "LOCKUP_CONTENT_TYPE_VIDEO"

/** First page: browse tab rich grid. Later pages: appendContinuationItemsAction. */
internal fun parseWebHomePage(
    root: JsonObject,
    hl: String?,
    now: Long = System.currentTimeMillis(),
): WebHomePage {
    val items = gridItems(root) + continuationItems(root)
    val videos =
        items
            .mapNotNull {
                it
                    .objectOrNull()
                    ?.get("richItemRenderer")
                    .objectOrNull()
                    ?.get("content")
                    .objectOrNull()
            }.mapNotNull { it["lockupViewModel"].objectOrNull() }
            .mapNotNull { parseLockup(it, hl, now) }
            .distinctBy { it.id }
    val next =
        items.firstNotNullOfOrNull { item ->
            item
                .objectOrNull()
                ?.get("continuationItemRenderer")
                .objectOrNull()
                ?.get("continuationEndpoint")
                .objectOrNull()
                ?.get("continuationCommand")
                .objectOrNull()
                ?.get("token")
                .stringOrNull()
        }
    return WebHomePage(videos, next)
}

private fun gridItems(root: JsonObject): List<JsonElement> =
    root["contents"]
        .objectOrNull()
        ?.get("twoColumnBrowseResultsRenderer")
        .objectOrNull()
        ?.get("tabs")
        .arrayOrNull()
        .orEmpty()
        .firstNotNullOfOrNull { tab ->
            tab
                .objectOrNull()
                ?.get("tabRenderer")
                .objectOrNull()
                ?.get("content")
                .objectOrNull()
                ?.get("richGridRenderer")
                .objectOrNull()
                ?.get("contents")
                .arrayOrNull()
        }.orEmpty()

private fun continuationItems(root: JsonObject): List<JsonElement> =
    root["onResponseReceivedActions"]
        .arrayOrNull()
        .orEmpty()
        .flatMap { action ->
            action
                .objectOrNull()
                ?.get("appendContinuationItemsAction")
                .objectOrNull()
                ?.get("continuationItems")
                .arrayOrNull()
                .orEmpty()
        }

private fun parseLockup(
    lockup: JsonObject,
    hl: String?,
    now: Long,
): Video? {
    if (lockup["contentType"].stringOrNull() != VIDEO_LOCKUP) return null
    val id = lockup["contentId"].stringOrNull() ?: return null
    val metadata = lockup["metadata"].objectOrNull()?.get("lockupMetadataViewModel").objectOrNull() ?: return null
    val title = metadata["title"].youtubeText()?.takeIf { it.isNotBlank() } ?: return null
    val avatar = metadata["image"].objectOrNull()?.get("decoratedAvatarViewModel").objectOrNull()
    val rows =
        metadata["metadata"]
            .objectOrNull()
            ?.get("contentMetadataViewModel")
            .objectOrNull()
            ?.get("metadataRows")
            .arrayOrNull()
            .orEmpty()
            .map { row ->
                row
                    .objectOrNull()
                    ?.get("metadataParts")
                    .arrayOrNull()
                    .orEmpty()
                    .mapNotNull { it.objectOrNull()?.get("text").youtubeText() }
            }
    val thumbnail = lockup["contentImage"].objectOrNull()?.get("thumbnailViewModel").objectOrNull()
    val badges = badgeTexts(thumbnail)
    val (viewsText, uploadText, uploadTimestamp) = lockupDateAndViews(rows.getOrNull(1).orEmpty(), hl, now)
    val avatars = avatarUrls(avatar)
    val durationBadge = badges.firstOrNull { it.contains(":") }
    val live = viewsText?.contains("watching", ignoreCase = true) == true || (durationBadge == null && badges.isNotEmpty())
    return Video(
        id = id,
        title = title,
        channelName = rows.firstOrNull()?.firstOrNull().orEmpty(),
        channelId = channelId(avatar).orEmpty(),
        thumbnailUrl = largestSource(thumbnail?.get("image")) ?: "https://i.ytimg.com/vi/$id/hq720.jpg",
        duration = durationSeconds(durationBadge),
        viewCount = parseYouTubeViewCount(viewsText, hl),
        uploadDate = uploadText,
        timestamp = uploadTimestamp ?: 0L,
        channelThumbnailUrl = avatars.firstOrNull().orEmpty(),
        channelThumbnailUrls = avatars,
        isLive = live,
    )
}

private fun badgeTexts(thumbnail: JsonObject?): List<String> =
    thumbnail
        ?.get("overlays")
        .arrayOrNull()
        .orEmpty()
        .flatMap { overlay ->
            overlay
                .objectOrNull()
                ?.get("thumbnailBottomOverlayViewModel")
                .objectOrNull()
                ?.get("badges")
                .arrayOrNull()
                .orEmpty()
        }.mapNotNull {
            it
                .objectOrNull()
                ?.get("thumbnailBadgeViewModel")
                .objectOrNull()
                ?.get("text")
                .stringOrNull()
        }

private fun channelId(avatar: JsonObject?): String? =
    avatar
        ?.get("rendererContext")
        .objectOrNull()
        ?.get("commandContext")
        .objectOrNull()
        ?.get("onTap")
        .objectOrNull()
        ?.get("innertubeCommand")
        .objectOrNull()
        ?.get("browseEndpoint")
        .objectOrNull()
        ?.get("browseId")
        .stringOrNull()
        ?.takeIf { it.startsWith("UC") }

private fun avatarUrls(avatar: JsonObject?): List<String> =
    avatar
        ?.get("avatar")
        .objectOrNull()
        ?.get("avatarViewModel")
        .objectOrNull()
        ?.get("image")
        .objectOrNull()
        ?.get("sources")
        .arrayOrNull()
        .orEmpty()
        .mapNotNull {
            it
                .objectOrNull()
                ?.get("url")
                .stringOrNull()
                ?.let(::normalizeImageUrl)
        }.take(1)

private fun largestSource(image: JsonElement?): String? =
    image
        .objectOrNull()
        ?.get("sources")
        .arrayOrNull()
        .orEmpty()
        .mapNotNull { it.objectOrNull() }
        .maxByOrNull { it["width"].stringOrNull()?.toIntOrNull() ?: 0 }
        ?.get("url")
        .stringOrNull()
        ?.let(::normalizeImageUrl)

private fun durationSeconds(text: String?): Int {
    val parts = text?.split(":")?.map { it.trim().toIntOrNull() ?: return 0 } ?: return 0
    return when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        else -> 0
    }
}
