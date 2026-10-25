package io.github.aedev.flow.bilibili

// Search endpoint JSON; one row shape for video, user, live rows.

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SearchResponse(
    val code: Int = 0,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val numPages: Int = 0,
        val result: List<Item>? = null,
    )

    /** One row; video-row and user-row fields all optional. */
    @Serializable
    data class Item(
        val type: String = "",
        val bvid: String = "",
        val aid: Long = 0,
        val title: String = "",
        val pic: String = "",
        val duration: String = "",
        val play: Long = 0,
        val author: String = "",
        val mid: Long = 0,
        val upic: String = "",
        val pubdate: Long = 0,
        val uname: String = "",
        val usign: String = "",
        val fans: Long = 0,
        val videos: Int = 0,
        /** Comma-separated uploader tags. */
        val tag: String = "",
        /** Partition, as in view response. */
        val typename: String = "",
        /** Live-room row: room number, cover ("//i0.hdslb.com/..."), viewer count, avatar, start time. */
        val roomid: Long = 0,
        @SerialName("user_cover") val userCover: String = "",
        val uface: String = "",
        val online: Long = 0,
        @SerialName("live_time") val liveTime: String = "",
        @SerialName("live_status") val liveStatus: Int = 0,
    )
}
