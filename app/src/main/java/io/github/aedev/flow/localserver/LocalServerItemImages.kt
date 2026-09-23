package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * The server's pages show one image per item. NewPipeExtractor keeps a list of sizes; these read the
 * biggest one and write a single-entry list, so the pages need not know about the list.
 */
internal var InfoItem.thumbnailUrl: String?
    get() = thumbnails.bestUrl()
    set(value) {
        thumbnails = value.asImages()
    }

internal var StreamInfoItem.uploaderAvatarUrl: String?
    get() = uploaderAvatars.bestUrl()
    set(value) {
        setUploaderAvatars(value.asImages())
    }

internal var CommentsInfoItem.uploaderAvatarUrl: String?
    get() = uploaderAvatars.bestUrl()
    set(value) {
        setUploaderAvatars(value.asImages())
    }

private fun List<Image>?.bestUrl(): String? = this?.maxByOrNull { it.height }?.url

private fun String?.asImages(): List<Image> =
    if (isNullOrBlank()) emptyList() else listOf(Image(this, Image.HEIGHT_UNKNOWN, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN))
