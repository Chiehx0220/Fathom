/*
 * Response shapes follow the JSON that PipePipeExtractor (GPL-3.0, commit
 * aef9726d5b1172213066f60bc338eb4278651d61) reads in BillibiliStreamExtractor.java.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region What callers get back

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
