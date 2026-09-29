/*
 * The JSON that Bilibili's endpoints return, as PipePipeExtractor (GPL-3.0, commit
 * aef9726d5b1172213066f60bc338eb4278651d61) reads it. Only the fields Flow uses are declared.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ViewResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ViewData? = null,
) {
    @Serializable
    data class ViewData(
        val bvid: String = "",
        val aid: Long = 0,
        val pic: String = "",
        val title: String = "",
        val desc: String = "",
        val pubdate: Long = 0,
        val duration: Int = 0,
        /** The partition the uploader filed it under: "游戏", "生活", ... */
        val tname: String = "",
        val owner: Owner = Owner(),
        val stat: Stat = Stat(),
        val pages: List<Page> = emptyList(),
        val rights: Rights = Rights(),
    )

    @Serializable
    data class Owner(
        val mid: Long = 0,
        val name: String = "",
        val face: String = "",
    )

    @Serializable
    data class Stat(
        val view: Long = 0,
        val like: Long = 0,
    )

    @Serializable
    data class Page(
        val cid: Long = 0,
        val page: Int = 1,
        val part: String = "",
        val duration: Int = 0,
    )

    @Serializable
    data class Rights(
        val pay: Int = 0,
    )
}

@Serializable
internal data class PlayUrlResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlayUrlData? = null,
) {
    @Serializable
    data class PlayUrlData(
        val dash: Dash? = null,
    )

    @Serializable
    data class Dash(
        val video: List<DashItem>? = null,
        val audio: List<DashItem>? = null,
        val dolby: Dolby? = null,
        val flac: Flac? = null,
    )

    @Serializable
    data class Dolby(
        val audio: List<DashItem>? = null,
    )

    @Serializable
    data class Flac(
        val audio: DashItem? = null,
    )

    @Serializable
    data class DashItem(
        val id: Int = 0,
        @SerialName("baseUrl") val baseUrl: String = "",
        @SerialName("backupUrl") val backupUrl: List<String>? = null,
        val bandwidth: Int = 0,
        val codecs: String = "",
        val width: Int = 0,
        val height: Int = 0,
        @SerialName("frameRate") val frameRate: String? = null,
        @SerialName("SegmentBase") val segmentBase: SegmentBase? = null,
    )

    @Serializable
    data class SegmentBase(
        @SerialName("Initialization") val initialization: String? = null,
        @SerialName("indexRange") val indexRange: String? = null,
    )
}

@Serializable
internal data class PlayerV2Response(
    val code: Int = 0,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        @SerialName("view_points") val viewPoints: List<ViewPoint> = emptyList(),
    )

    @Serializable
    data class ViewPoint(
        val content: String = "",
        val from: Long = 0,
        val imgUrl: String = "",
    )
}

@Serializable
internal data class RelatedResponse(
    val code: Int = 0,
    val data: List<Item>? = null,
) {
    @Serializable
    data class Item(
        val bvid: String = "",
        val aid: Long = 0,
        val title: String = "",
        val pic: String = "",
        val duration: Int = 0,
        val pubdate: Long = 0,
        val tname: String = "",
        val owner: ViewResponse.Owner = ViewResponse.Owner(),
        val stat: ViewResponse.Stat = ViewResponse.Stat(),
    )
}

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

    /** One row; the fields a video row and a user row each use are all optional here. */
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
        /** Comma-separated tags the uploader gave the video. */
        val tag: String = "",
        /** The partition, as on a view response. */
        val typename: String = "",
        /** A live-room row's room number, cover ("//i0.hdslb.com/..."), viewer count, avatar and start time. */
        val roomid: Long = 0,
        @SerialName("user_cover") val userCover: String = "",
        val uface: String = "",
        val online: Long = 0,
        @SerialName("live_time") val liveTime: String = "",
        @SerialName("live_status") val liveStatus: Int = 0,
    )
}

@Serializable
internal data class CodeOnly(
    val code: Int = 0,
    val message: String = "",
)

@Serializable
internal data class CardResponse(
    val code: Int = 0,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val card: Card = Card(),
        val space: Space? = null,
    )

    @Serializable
    data class Card(
        val name: String = "",
        val face: String = "",
        val fans: Long = 0,
        val sign: String = "",
    )

    @Serializable
    data class Space(
        @SerialName("l_img") val banner: String = "",
    )
}

/** The web (`x/space/wbi/arc/search`) and search (`recArchivesByKeywords`) list shapes together. */
@Serializable
internal data class UserVideosResponse(
    val code: Int = 0,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val list: ListPart? = null,
        val archives: List<Item>? = null,
        val item: List<Item>? = null,
    )

    @Serializable
    data class ListPart(
        val vlist: List<Item>? = null,
    )

    @Serializable
    data class Item(
        val aid: Long = 0,
        val bvid: String = "",
        val title: String = "",
        val pic: String = "",
        val play: Long = 0,
        val created: Long = 0,
        val pubdate: Long = 0,
        val length: String = "",
        val duration: Int = 0,
        val author: String = "",
        val stat: ViewResponse.Stat = ViewResponse.Stat(),
        val cover: String = "",
        val param: String = "",
        val ctime: Long = 0,
    )
}

@Serializable
internal data class PlaylistListResponse(
    val code: Int = 0,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        @SerialName("items_lists") val itemsLists: Lists = Lists(),
    )

    @Serializable
    data class Lists(
        @SerialName("seasons_list") val seasons: List<Entry>? = null,
        @SerialName("series_list") val series: List<Entry>? = null,
    )

    @Serializable
    data class Entry(
        val meta: Meta = Meta(),
    )

    @Serializable
    data class Meta(
        val name: String = "",
        val mid: Long = 0,
        @SerialName("season_id") val seasonId: Long = 0,
        @SerialName("series_id") val seriesId: Long = 0,
        val cover: String = "",
        val total: Int = 0,
    )
}

@Serializable
internal data class PlaylistArchivesResponse(
    val code: Int = 0,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val archives: List<UserVideosResponse.Item>? = null,
        val page: Page = Page(),
        val meta: PlaylistListResponse.Meta = PlaylistListResponse.Meta(),
    )

    @Serializable
    data class Page(
        val total: Int = 0,
    )
}

@Serializable
internal data class CommentsResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val replies: List<Reply>? = null,
        @SerialName("top_replies") val topReplies: List<Reply>? = null,
        val cursor: Cursor = Cursor(),
    )

    @Serializable
    data class Cursor(
        @SerialName("is_end") val isEnd: Boolean = true,
        @SerialName("pagination_reply") val paginationReply: PaginationReply = PaginationReply(),
    )

    @Serializable
    data class PaginationReply(
        @SerialName("next_offset") val nextOffset: String = "",
    )

    @Serializable
    data class Reply(
        @SerialName("rpid_str") val rpidStr: String = "",
        val mid: Long = 0,
        val like: Long = 0,
        val ctime: Long = 0,
        val rcount: Int = 0,
        val member: Member = Member(),
        val content: Content = Content(),
        @SerialName("up_action") val upAction: UpAction = UpAction(),
    )

    @Serializable
    data class Member(
        val uname: String = "",
        val avatar: String = "",
    )

    @Serializable
    data class Content(
        val message: String = "",
    )

    @Serializable
    data class UpAction(
        val like: Boolean = false,
    )
}

@Serializable
internal data class PopularResponse(
    val code: Int = 0,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val list: List<RelatedResponse.Item>? = null,
    )
}

@Serializable
internal data class RoomBaseInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        /** Keyed by the room id that was asked for, which may be the short one. */
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
        /** 0 not live, 1 live, 2 rebroadcast of uploaded videos. */
        @SerialName("live_status") val liveStatus: Int = 0,
        /** "yyyy-MM-dd HH:mm:ss" in China time; "0000-00-00 00:00:00" when not live. */
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
        /** An HLS master playlist listing every quality the viewer may watch; on the fmp4 entry only. */
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
    /** Keyed by uid; an uploader with no room gets an empty array instead, which does not decode. */
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
