/*
 * [BilibiliApi] results; JSON shapes in BilibiliWire.kt.
 * Modelled on PipePipeExtractor (GPL-3.0, commit aef9726d5b1172213066f60bc338eb4278651d61).
 */
package io.github.aedev.flow.bilibili

// region Video

data class BilibiliUploader(
    val mid: Long,
    val name: String,
    val avatarUrl: String,
)

data class BilibiliPage(
    val cid: Long,
    val page: Int,
    val title: String,
    val durationSec: Int,
)

data class BilibiliVideoInfo(
    val bvid: String,
    val aid: Long,
    /** cid of the part; keys playback and danmaku. */
    val cid: Long,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val durationSec: Int,
    val uploader: BilibiliUploader,
    val viewCount: Long,
    val likeCount: Long,
    val uploadTimeSec: Long,
    val pages: List<BilibiliPage>,
    /** Paid video; [BilibiliPlayback] may be empty. */
    val isPaid: Boolean,
    /** Uploader's partition; "" if unknown. */
    val category: String = "",
)

/** DASH stream; [initRange], [indexRange]: inclusive byte ranges into [url]. */
data class BilibiliStreamFormat(
    /** Bilibili quality id: 120 = 4K, 80 = 1080p, 30280 = 192k audio. */
    val id: Int,
    val url: String,
    val backupUrls: List<String>,
    val codecs: String,
    val bandwidth: Int,
    val width: Int,
    val height: Int,
    val frameRate: String?,
    val initRange: LongRange?,
    val indexRange: LongRange?,
)

data class BilibiliPlayback(
    val info: BilibiliVideoInfo,
    val videoFormats: List<BilibiliStreamFormat>,
    val audioFormats: List<BilibiliStreamFormat>,
    /** CDN requires bilibili.com Referer. */
    val requestHeaders: Map<String, String>,
)

data class BilibiliChapter(
    val title: String,
    val startSeconds: Int,
    val imageUrl: String?,
)

data class BilibiliRelated(
    val bvid: String,
    val title: String,
    val thumbnailUrl: String,
    val durationSec: Int,
    val viewCount: Long,
    val uploader: BilibiliUploader,
    val uploadTimeSec: Long,
    val category: String = "",
)

// endregion

// region Search

enum class BilibiliSearchType(
    val apiValue: String,
) {
    VIDEO("video"),
    USER("bili_user"),
    LIVE("live_room"),
}

sealed interface BilibiliSearchItem {
    data class Video(
        val bvid: String,
        val title: String,
        val thumbnailUrl: String,
        val durationSec: Int,
        val viewCount: Long,
        val uploader: BilibiliUploader,
        val uploadTimeSec: Long,
        val category: String = "",
        /** Uploader tags. */
        val tags: List<String> = emptyList(),
    ) : BilibiliSearchItem

    data class User(
        val mid: Long,
        val name: String,
        val avatarUrl: String,
        val description: String,
        val followerCount: Long,
        val videoCount: Int,
    ) : BilibiliSearchItem

    data class Live(
        val room: BilibiliLiveItem,
    ) : BilibiliSearchItem
}

data class BilibiliSearchPage(
    val items: List<BilibiliSearchItem>,
    val hasMore: Boolean,
)

// endregion

// region Live

enum class BilibiliLiveStatus { OFFLINE, LIVE, REBROADCAST }

/** Room page: uploader, title, start time. */
data class BilibiliLiveRoom(
    /** Long room number (link may carry a short alias). */
    val roomId: Long,
    val title: String,
    val coverUrl: String,
    val uploader: BilibiliUploader,
    val viewerCount: Long,
    /** Stream start, epoch seconds; 0 unknown. */
    val startedAtSec: Long,
    val tags: List<String>,
    val status: BilibiliLiveStatus,
)

/** Room playback source: HLS master playlist (all qualities) or single FLV. */
data class BilibiliLiveStreams(
    val hlsMasterUrl: String?,
    val flvUrl: String?,
)

data class BilibiliLivePlayback(
    val room: BilibiliLiveRoom,
    val streams: BilibiliLiveStreams,
)

/** Room list item: recommendations, search. */
data class BilibiliLiveItem(
    val roomId: Long,
    val title: String,
    val coverUrl: String,
    val uploaderName: String,
    val uploaderAvatarUrl: String,
    val viewerCount: Long,
    val startedAtSec: Long = 0,
)

/** Chat socket endpoint + auth. */
data class BilibiliLiveChatAccess(
    val roomId: Long,
    val token: String,
    /** "wss://host:port/sub", best first. */
    val endpoints: List<String>,
    val buvid: String?,
)

/** Chat event. */
sealed interface BilibiliLiveMessage {
    data class Chat(
        val text: String,
        val argbColor: Int,
        val position: BilibiliDanmakuPosition,
        val author: String,
        val avatarUrl: String?,
    ) : BilibiliLiveMessage

    data class SuperChat(
        val message: String,
        val priceYuan: Int,
        val argbColor: Int,
        val headerArgbColor: Int,
        val author: String,
        val avatarUrl: String?,
    ) : BilibiliLiveMessage {
        /** Danmaku layer form. */
        val text: String get() = "(¥$priceYuan) $message"
    }
}

// endregion

// region Uploader space

data class BilibiliChannelInfo(
    val mid: Long,
    val name: String,
    val avatarUrl: String,
    val bannerUrl: String?,
    val followerCount: Long,
    val description: String,
)

data class BilibiliChannelVideo(
    val bvid: String,
    val aid: Long,
    val title: String,
    val thumbnailUrl: String,
    val durationSec: Int,
    val viewCount: Long,
    val uploadTimeSec: Long,
    val authorName: String,
)

data class BilibiliChannelVideosPage(
    val videos: List<BilibiliChannelVideo>,
    val hasMore: Boolean,
    /** App API pages by last video's aid. */
    val lastAid: Long,
)

/** Uploader video-list position: web API by number, app API by aid. */
data class BilibiliChannelPageKey(
    val page: Int,
    val lastAid: Long,
)

enum class BilibiliPlaylistKind { SERIES, SEASON }

/** Series/season. */
data class BilibiliPlaylistRef(
    val kind: BilibiliPlaylistKind,
    val mid: Long,
    val id: Long,
    val name: String,
    val coverUrl: String,
    val videoCount: Int,
)

data class BilibiliPlaylistPage(
    val items: List<BilibiliPlaylistRef>,
    val hasMore: Boolean,
)

data class BilibiliPlaylistVideos(
    val videos: List<BilibiliChannelVideo>,
    val total: Int,
)

// endregion

// region Comments

data class BilibiliComment(
    val id: String,
    val authorMid: Long,
    val authorName: String,
    val authorAvatarUrl: String,
    val text: String,
    val likeCount: Long,
    val postedSec: Long,
    val replyCount: Int,
    val isPinned: Boolean,
    val isLikedByUploader: Boolean,
)

/** [nextOffset] null on last page. */
data class BilibiliCommentsPage(
    val comments: List<BilibiliComment>,
    val nextOffset: String?,
)

/** [nextPage] null on last page. */
data class BilibiliRepliesPage(
    val replies: List<BilibiliComment>,
    val nextPage: Int?,
)

// endregion

// region Failures

/** Distinguishes Bilibili failure from plain network error. */
sealed class BilibiliException(
    message: String,
) : Exception(message)

class BilibiliContentNotAvailableException(
    message: String,
) : BilibiliException(message)

class BilibiliGeoRestrictedException(
    message: String,
) : BilibiliException(message)

class BilibiliPaidContentException(
    message: String,
) : BilibiliException(message)

class BilibiliLiveNotStartedException(
    message: String,
) : BilibiliException(message)

/** Room replays uploaded videos; unsupported by the live player. */
class BilibiliLiveRebroadcastException(
    message: String,
) : BilibiliException(message)

// endregion

internal fun String.toHttps(): String = replace("http:", "https:")
