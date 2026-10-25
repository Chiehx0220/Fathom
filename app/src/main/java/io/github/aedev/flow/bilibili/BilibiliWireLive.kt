package io.github.aedev.flow.bilibili

// Live endpoint JSON: room info, play info, chat access, recommendations, status by uid.

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class RoomBaseInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        /** Keyed by long room id even when asked by short id. */
        @SerialName("by_room_ids") val byRoomIds: Map<String, Room> = emptyMap(),
    )

    @Serializable
    data class Room(
        @SerialName("room_id") val roomId: Long = 0,
        val uid: Long = 0,
        val uname: String = "",
        val title: String = "",
        val cover: String = "",
        @SerialName("cover_from_user") val coverFromUser: String? = null,
        val face: String? = null,
        val online: Long = 0,
        /** 0 offline, 1 live, 2 rebroadcast. */
        @SerialName("live_status") val liveStatus: Int = 0,
        /** "yyyy-MM-dd HH:mm:ss" China time; "0000-00-00 00:00:00" offline. */
        @SerialName("live_time") val liveTime: String? = null,
        @SerialName("tag_name") val tagName: String = "",
        val tags: String = "",
    )
}

@Serializable
internal data class RoomPlayInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        @SerialName("playurl_info") val playurlInfo: PlayurlInfo? = null,
    )

    @Serializable
    data class PlayurlInfo(
        val playurl: Playurl? = null,
    )

    @Serializable
    data class Playurl(
        val stream: List<Stream> = emptyList(),
    )

    @Serializable
    data class Stream(
        @SerialName("protocol_name") val protocolName: String = "",
        val format: List<Format> = emptyList(),
    )

    @Serializable
    data class Format(
        @SerialName("format_name") val formatName: String = "",
        /** HLS master playlist, all viewer-allowed qualities; fmp4 entry only. */
        @SerialName("master_url") val masterUrl: String? = null,
        val codec: List<Codec> = emptyList(),
    )

    @Serializable
    data class Codec(
        @SerialName("codec_name") val codecName: String = "",
        @SerialName("base_url") val baseUrl: String = "",
        @SerialName("url_info") val urlInfo: List<UrlInfo> = emptyList(),
    )

    @Serializable
    data class UrlInfo(
        val host: String = "",
        val extra: String = "",
    )
}

@Serializable
internal data class DanmuInfoResponse(
    val code: Int = 0,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val token: String = "",
        @SerialName("host_list") val hostList: List<Host> = emptyList(),
    )

    @Serializable
    data class Host(
        val host: String = "",
        @SerialName("wss_port") val wssPort: Int = 443,
    )
}

@Serializable
internal data class RecommendedLivesResponse(
    val code: Int = 0,
    val data: List<Item>? = null,
) {
    @Serializable
    data class Item(
        val roomid: Long = 0,
        val title: String = "",
        val uname: String = "",
        val face: String = "",
        @SerialName("user_cover") val userCover: String = "",
        @SerialName("system_cover") val systemCover: String = "",
        @SerialName("watched_show") val watchedShow: Watched? = null,
    )

    @Serializable
    data class Watched(
        val num: Long = 0,
    )
}

@Serializable
internal data class LiveStatusByUidsResponse(
    val code: Int = 0,
    /** Keyed by uid; no room → empty array (does not decode). */
    val data: Map<String, Room>? = null,
) {
    @Serializable
    data class Room(
        val title: String = "",
        @SerialName("room_id") val roomId: Long = 0,
        @SerialName("live_status") val liveStatus: Int = 0,
        @SerialName("cover_from_user") val coverFromUser: String = "",
        val online: Long = 0,
        val uname: String = "",
        val face: String = "",
    )
}
