package io.github.aedev.flow.bilibili

/*
 * Video endpoint JSON (view, playurl, player v2, related, popular), as PipePipeExtractor (GPL-3.0, commit aef9726d5b1172213066f60bc338eb4278651d61) reads it.
 * Used fields only.
 */

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
        /** Uploader's partition: "游戏", "生活", ... */
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
internal data class PopularResponse(
    val code: Int = 0,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val list: List<RelatedResponse.Item>? = null,
    )
}
