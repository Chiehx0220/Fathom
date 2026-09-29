/*
 * What Flow's code gets back from [BilibiliApi]. The JSON shapes live in BilibiliWire.kt.
 * Modelled on the data PipePipeExtractor (GPL-3.0, commit aef9726d5b1172213066f60bc338eb4278651d61)
 * reads from Bilibili.
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
    /** cid of the requested part; playback and danmaku are keyed on it. */
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
    /** True when the video needs payment; its [BilibiliPlayback] may come back empty. */
    val isPaid: Boolean,
    /** The partition the uploader filed it under, "" when unknown. */
    val category: String = "",
)

/** One DASH stream. [initRange] and [indexRange] are inclusive byte ranges into [url]. */
data class BilibiliStreamFormat(
    /** Bilibili's quality id: 120 = 4K, 80 = 1080p, 30280 = 192k audio, and so on. */
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
    /** Bilibili's CDN refuses media requests without a bilibili.com Referer. */
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
        /** The tags the uploader gave it. */
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

/** A live room's page: who is streaming, what, and since when. */
data class BilibiliLiveRoom(
    /** The long room number; the one in a link may be a short alias of it. */
    val roomId: Long,
    val title: String,
    val coverUrl: String,
    val uploader: BilibiliUploader,
    val viewerCount: Long,
    /** Epoch seconds the stream started, 0 when unknown. */
    val startedAtSec: Long,
    val tags: List<String>,
    val status: BilibiliLiveStatus,
)

/** What a live room can be played from: an HLS master playlist with every quality, or a single FLV. */
data class BilibiliLiveStreams(
    val hlsMasterUrl: String?,
    val flvUrl: String?,
)

data class BilibiliLivePlayback(
    val room: BilibiliLiveRoom,
    val streams: BilibiliLiveStreams,
)

/** A live room in a list: recommendations and search results. */
data class BilibiliLiveItem(
    val roomId: Long,
    val title: String,
    val coverUrl: String,
    val uploaderName: String,
    val uploaderAvatarUrl: String,
    val viewerCount: Long,
    val startedAtSec: Long = 0,
)

/** Where and how to open a room's chat socket. */
data class BilibiliLiveChatAccess(
    val roomId: Long,
    val token: String,
    /** Full "wss://host:port/sub" addresses, best first. */
    val endpoints: List<String>,
    val buvid: String?,
)

/** One event of a room's chat. */
sealed interface BilibiliLiveMessage {
    data class Chat(
        val text: String,
        val argbColor: Int,
        val position: BilibiliDanmakuPosition,
    ) : BilibiliLiveMessage

    data class SuperChat(
        val text: String,
        val priceYuan: Int,
        val argbColor: Int,
    ) : BilibiliLiveMessage
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
    /** The app API pages by the aid of the last video. */
    val lastAid: Long,
)

/** Position in an uploader's video list: the web APIs page by number, the app API by aid. */
data class BilibiliChannelPageKey(
    val page: Int,
    val lastAid: Long,
)

enum class BilibiliPlaylistKind { SERIES, SEASON }

/** A series or season an uploader published. */
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

/** [nextOffset] is null on the last page. */
data class BilibiliCommentsPage(
    val comments: List<BilibiliComment>,
    val nextOffset: String?,
)

/** [nextPage] is null on the last page. */
data class BilibiliRepliesPage(
    val replies: List<BilibiliComment>,
    val nextPage: Int?,
)

// endregion

// region Failures

/** Lets callers tell a Bilibili failure from a plain network error. */
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

/** The room is replaying uploaded videos, which the live player does not cover. */
class BilibiliLiveRebroadcastException(
    message: String,
) : BilibiliException(message)

// endregion

internal fun String.toHttps(): String = replace("http:", "https:")
