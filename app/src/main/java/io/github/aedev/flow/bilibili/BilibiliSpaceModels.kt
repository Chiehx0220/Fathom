package io.github.aedev.flow.bilibili

// Uploader space results; wire: BilibiliWireSpace.kt.

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

/** Uploader video-list order; [order] is the API's `order` value. */
enum class BilibiliChannelSort(
    val order: String,
) {
    LATEST("pubdate"),
    POPULAR("click"),
}

/** Uploader video-list position: web API by number, app API by aid. */
data class BilibiliChannelPageKey(
    val page: Int,
    val lastAid: Long,
    val sort: BilibiliChannelSort = BilibiliChannelSort.LATEST,
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
