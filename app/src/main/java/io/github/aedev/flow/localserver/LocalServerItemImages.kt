package io.github.aedev.flow.localserver

import io.github.aedev.flow.content.CommentsInfoItem
import io.github.aedev.flow.content.Image
import io.github.aedev.flow.content.InfoItem
import io.github.aedev.flow.content.StreamInfoItem

/** One image per item: largest of the size list → single entry. */
internal var InfoItem.thumbnailUrl: String?
    get() = thumbnails.bestUrl()
    set(value) {
        thumbnails = value.asImages()
    }

internal var StreamInfoItem.uploaderAvatarUrl: String?
    get() = uploaderAvatars.bestUrl()
    set(value) {
        uploaderAvatars = value.asImages()
    }

internal var CommentsInfoItem.uploaderAvatarUrl: String?
    get() = uploaderAvatars.bestUrl()
    set(value) {
        uploaderAvatars = value.asImages()
    }

private fun List<Image>?.bestUrl(): String? = this?.maxByOrNull { it.height }?.url

private fun String?.asImages(): List<Image> =
    if (isNullOrBlank()) emptyList() else listOf(Image(this, Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN))
