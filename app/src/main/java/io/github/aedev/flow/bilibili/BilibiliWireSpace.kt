package io.github.aedev.flow.bilibili

// Uploader space JSON: card, video lists (web, keyword search, app), series/seasons.

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

/** Web (`x/space/wbi/arc/search`) and search (`recArchivesByKeywords`) list shapes. */

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
