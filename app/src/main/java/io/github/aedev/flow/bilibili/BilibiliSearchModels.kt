package io.github.aedev.flow.bilibili

// Search results; wire: SearchResponse.kt.

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
