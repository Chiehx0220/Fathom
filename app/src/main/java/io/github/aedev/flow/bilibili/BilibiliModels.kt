/*
 * Response shapes follow the JSON that PipePipeExtractor (GPL-3.0, commit
 * aef9726d5b1172213066f60bc338eb4278651d61) reads in BillibiliStreamExtractor.java.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region What callers get back

data class BilibiliChapter(
    val title: String,
    val startSeconds: Int,
    val imageUrl: String?,
)

/** One entry of a video's "related videos" lane. */
data class BilibiliRelated(
    val bvid: String,
    val title: String,
    val thumbnailUrl: String,
    val durationSec: Int,
    val viewCount: Long,
    val uploader: BilibiliUploader,
    val uploadTimeSec: Long,
)

enum class BilibiliSearchType(val apiValue: String) { VIDEO("video"), USER("bili_user") }

sealed interface BilibiliSearchItem {
    data class Video(
        val bvid: String,
        val title: String,
        val thumbnailUrl: String,
        val durationSec: Int,
        val viewCount: Long,
        val uploader: BilibiliUploader,
        val uploadTimeSec: Long,
    ) : BilibiliSearchItem

    data class User(
        val mid: Long,
        val name: String,
        val avatarUrl: String,
        val description: String,
        val followerCount: Long,
        val videoCount: Int,
    ) : BilibiliSearchItem
}

data class BilibiliSearchPage(
    val items: List<BilibiliSearchItem>,
    val hasMore: Boolean,
)

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
    /** aid of the last video, which the app API uses as its cursor for the next page. */
    val lastAid: Long,
)

/** Where a page sits in an uploader's video list: the web modes page by number, the app mode by aid. */
data class BilibiliChannelPageKey(
    val page: Int,
    val lastAid: Long,
)

enum class BilibiliPlaylistKind { SERIES, SEASON }

/** One uploader-made list (a series or a season) as the channel's playlist tab shows it. */
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
    /** cid of the requested part; the playback request is keyed on it. */
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
    /** True when the video needs payment; [BilibiliPlayback] may come back empty for it. */
    val isPaid: Boolean,
)

/** One DASH representation. [initRange] and [indexRange] are inclusive byte ranges into [url]. */
data class BilibiliStreamFormat(
    /** Bilibili's quality id (qn): 120 = 4K, 80 = 1080p, 30280 = 192k audio, and so on. */
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
    /**
     * Headers the CDN needs on every media request (Referer and User-Agent; Bilibili's CDN refuses
     * a request without a bilibili.com Referer). Pass these to the player's data source.
     */
    val requestHeaders: Map<String, String>,
)

// endregion

// region Failures

/** Base type so callers can tell a Bilibili failure from a generic IO error and pick the right message. */
sealed class BilibiliException(message: String) : Exception(message)

class BilibiliContentNotAvailableException(message: String) : BilibiliException(message)

class BilibiliGeoRestrictedException(message: String) : BilibiliException(message)

class BilibiliPaidContentException(message: String) : BilibiliException(message)

// endregion

// region Wire format

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

// endregion

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
