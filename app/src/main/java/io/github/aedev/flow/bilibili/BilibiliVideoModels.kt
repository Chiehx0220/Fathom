package io.github.aedev.flow.bilibili

/*
 * [BilibiliApi] video results; JSON shapes in BilibiliWireVideo.kt.
 * Modelled on PipePipeExtractor (GPL-3.0, commit aef9726d5b1172213066f60bc338eb4278651d61).
 */

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
    /** cid of the part; keys playback and danmaku. */
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
    /** Paid video; [BilibiliPlayback] may be empty. */
    val isPaid: Boolean,
    /** Uploader's partition; "" if unknown. */
    val category: String = "",
)

/** DASH stream; [initRange], [indexRange]: inclusive byte ranges into [url]. */
data class BilibiliStreamFormat(
    /** Bilibili quality id: 120 = 4K, 80 = 1080p, 30280 = 192k audio. */
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
    /** CDN requires bilibili.com Referer. */
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
