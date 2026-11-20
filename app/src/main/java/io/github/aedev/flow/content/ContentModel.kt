package io.github.aedev.flow.content

import io.github.aedev.flow.player.stream.model.AudioStream
import io.github.aedev.flow.player.stream.model.StreamType
import io.github.aedev.flow.player.stream.model.VideoStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/*
 * Service-neutral content types shared by the local server, the Bilibili client glue and saved-data
 * code. They replace the NewPipeExtractor item classes that upstream Flow removed; the shape follows
 * those classes so call sites changed only in their imports. No network or parsing code lives here.
 */

/** Service ids as stored in saved data. Bilibili's is [io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID]. */
object ServiceIds {
    const val YOUTUBE = 0
}

/** Continuation cursor of a paged list: whichever of these fields the owning source filled in. */
class Page(
    val url: String? = null,
    val id: String? = null,
    val ids: List<String>? = null,
    val cookies: Map<String, String>? = null,
    val body: ByteArray? = null,
) : java.io.Serializable

class Image(
    val url: String,
    val height: Int = HEIGHT_UNKNOWN,
    val width: Int = WIDTH_UNKNOWN,
    val estimatedResolutionLevel: ResolutionLevel = ResolutionLevel.UNKNOWN,
) {
    enum class ResolutionLevel { HIGH, MEDIUM, LOW, UNKNOWN }

    companion object {
        const val HEIGHT_UNKNOWN = -1
        const val WIDTH_UNKNOWN = -1
    }
}

class Description(
    val content: String,
    val type: Int,
) {
    companion object {
        const val HTML = 1
        const val MARKDOWN = 2
        const val PLAIN_TEXT = 3
        val EMPTY_DESCRIPTION = Description("", PLAIN_TEXT)
    }

    override fun equals(other: Any?): Boolean = other is Description && other.content == content && other.type == type

    override fun hashCode(): Int = 31 * content.hashCode() + type
}

class DateWrapper(
    val instant: Instant,
    val isApproximation: Boolean = false,
) {
    constructor(date: OffsetDateTime, isApproximation: Boolean = false) : this(date.toInstant(), isApproximation)

    fun offsetDateTime(): OffsetDateTime = OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
}

enum class InfoType { STREAM, PLAYLIST, CHANNEL, COMMENT }

sealed class InfoItem(
    val infoType: InfoType,
    val serviceId: Int,
    val url: String,
    val name: String,
) {
    var thumbnails: List<Image> = emptyList()
}

class StreamInfoItem(
    serviceId: Int,
    url: String,
    name: String,
    val streamType: StreamType,
) : InfoItem(InfoType.STREAM, serviceId, url, name) {
    var uploaderName: String? = null
    var viewCount: Long = -1
    var duration: Long = -1
    var uploaderUrl: String? = null
    var uploaderAvatars: List<Image> = emptyList()
    var shortDescription: String? = null
    var textualUploadDate: String? = null
    var uploadDate: DateWrapper? = null
    var isUploaderVerified: Boolean = false
    var isShortFormContent: Boolean = false
}

class ChannelInfoItem(
    serviceId: Int,
    url: String,
    name: String,
) : InfoItem(InfoType.CHANNEL, serviceId, url, name) {
    var description: String? = null
    var subscriberCount: Long = -1
    var streamCount: Long = -1
    var isVerified: Boolean = false
}

class PlaylistInfoItem(
    serviceId: Int,
    url: String,
    name: String,
) : InfoItem(InfoType.PLAYLIST, serviceId, url, name) {
    var uploaderName: String? = null
    var uploaderUrl: String? = null
    var isUploaderVerified: Boolean = false
    var streamCount: Long = 0
    var description: Description? = null
}

class CommentsInfoItem(
    serviceId: Int,
    url: String,
    name: String,
) : InfoItem(InfoType.COMMENT, serviceId, url, name) {
    var commentId: String? = null
    var commentText: Description = Description.EMPTY_DESCRIPTION
    var uploaderName: String? = null
    var uploaderAvatars: List<Image> = emptyList()
    var uploaderUrl: String? = null
    var textualUploadDate: String? = null
    var uploadDate: DateWrapper? = null
    var likeCount: Int = NO_LIKE_COUNT
    var textualLikeCount: String? = null
    var isHeartedByUploader: Boolean = false
    var isPinned: Boolean = false
    var isUploaderVerified: Boolean = false
    var streamPosition: Int = NO_STREAM_POSITION
    var replyCount: Int = UNKNOWN_REPLY_COUNT
    var replies: Page? = null
    var isChannelOwner: Boolean = false
    var hasCreatorReply: Boolean = false
    var isEdited: Boolean = false

    companion object {
        const val NO_LIKE_COUNT = -1
        const val NO_STREAM_POSITION = -1
        const val UNKNOWN_REPLY_COUNT = -1
    }
}

class StreamSegment(
    var title: String?,
    var startTimeSeconds: Int,
) {
    var channelName: String? = null
    var url: String? = null
    var previewUrl: String? = null
}

/** Storyboard sheets for seek previews. */
class Frameset(
    val urls: List<String>,
    val frameWidth: Int,
    val frameHeight: Int,
    val totalCount: Int,
    val durationPerFrame: Int,
    val framesPerPageX: Int,
    val framesPerPageY: Int,
)

class SubtitlesStream(
    val content: String,
    val languageTag: String,
    val isAutoGenerated: Boolean,
    val extension: String,
) {
    val locale: Locale get() = Locale.forLanguageTag(languageTag)
    val displayLanguageName: String get() = locale.getDisplayName(locale).ifBlank { languageTag }
}

/** One video's full details: what the watch page, downloads and subtitle routes read. */
class StreamInfo(
    val serviceId: Int,
    val url: String,
    val originalUrl: String,
    var streamType: StreamType,
    val id: String,
    val name: String,
    var ageLimit: Int = 0,
) {
    var thumbnails: List<Image> = emptyList()
    var textualUploadDate: String? = null
    var uploadDate: DateWrapper? = null
    var duration: Long = -1
    var description: Description = Description.EMPTY_DESCRIPTION
    var viewCount: Long = -1
    var likeCount: Long = -1
    var dislikeCount: Long = -1
    var uploaderName: String = ""
    var uploaderUrl: String = ""
    var uploaderAvatars: List<Image> = emptyList()
    var isUploaderVerified: Boolean = false
    var uploaderSubscriberCount: Long = -1
    var videoStreams: List<VideoStream> = emptyList()
    var audioStreams: List<AudioStream> = emptyList()
    var videoOnlyStreams: List<VideoStream> = emptyList()
    var dashMpdUrl: String = ""
    var hlsUrl: String = ""
    var relatedItems: List<InfoItem> = emptyList()
    var subtitles: List<SubtitlesStream> = emptyList()
    var category: String = ""
    var tags: List<String> = emptyList()
    var streamSegments: List<StreamSegment> = emptyList()
    var previewFrames: List<Frameset> = emptyList()
    var isShortFormContent: Boolean = false
}
