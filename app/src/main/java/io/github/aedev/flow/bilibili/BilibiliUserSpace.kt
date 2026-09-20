/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * services/bilibili/extractors/BilibiliChannelExtractor.java, BilibiliChannelTabExtractor.java,
 * BilibiliPlaylistExtractor.java and utils.encAppSign.
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

/**
 * An uploader's space: profile, videos, and the series and seasons they publish.
 *
 * Bilibili blocks these endpoints more readily than the rest, so the video list has three
 * interchangeable APIs (web, keyword search, app). A blocked one hands over to the next, and the
 * choice is kept for later calls, as PipePipe does.
 */
internal class BilibiliUserSpace(
    private val session: BilibiliSession,
    private val json: Json,
) {
    private val videoApiMode = AtomicInteger(MODE_WEB)

    suspend fun info(mid: Long): BilibiliChannelInfo {
        val body = request(mid, "$CARD_URL$mid")
        val data = decode<CardResponse>(body).data ?: throw BilibiliContentNotAvailableException("Channel $mid not found")
        return BilibiliChannelInfo(
            mid = mid,
            name = data.card.name,
            avatarUrl = data.card.face.toHttps(),
            bannerUrl = data.space?.banner?.takeIf { it.isNotBlank() }?.toHttps(),
            followerCount = data.card.fans,
            description = data.card.sign,
        )
    }

    suspend fun videos(
        mid: Long,
        key: BilibiliChannelPageKey,
    ): BilibiliChannelVideosPage {
        var failure: BilibiliException? = null
        repeat(VIDEO_API_MODES) {
            val mode = videoApiMode.get()
            try {
                return when (mode) {
                    MODE_WEB -> videosFrom(mid, webUrl(mid, key.page)) { it.data?.list?.vlist }
                    MODE_SEARCH -> videosFrom(mid, searchUrl(mid, key.page)) { it.data?.archives }
                    else -> videosFrom(mid, appUrl(mid, key.lastAid)) { it.data?.item }
                }
            } catch (e: BilibiliException) {
                failure = e
                Log.w(TAG, "Video list API $mode blocked: ${e.message?.take(80)}")
                videoApiMode.compareAndSet(mode, (mode + 1) % VIDEO_API_MODES)
            }
        }
        throw failure ?: BilibiliContentNotAvailableException("Every video list API is blocked")
    }

    suspend fun playlists(
        mid: Long,
        page: Int,
    ): BilibiliPlaylistPage {
        val url = "$SEASONS_SERIES_URL?mid=$mid&page_num=$page&page_size=10"
        val body = request(mid, url)
        val lists = decode<PlaylistListResponse>(body).data?.itemsLists ?: PlaylistListResponse.Lists()
        val refs =
            lists.seasons.orEmpty().map { toRef(BilibiliPlaylistKind.SEASON, it.meta) } +
                lists.series.orEmpty().map { toRef(BilibiliPlaylistKind.SERIES, it.meta) }
        return BilibiliPlaylistPage(refs.filter { it.videoCount > 0 }, hasMore = refs.isNotEmpty())
    }

    suspend fun playlistVideos(
        kind: BilibiliPlaylistKind,
        mid: Long,
        id: Long,
        page: Int,
    ): BilibiliPlaylistVideos {
        val url =
            when (kind) {
                BilibiliPlaylistKind.SEASON ->
                    "$SEASON_ARCHIVES_URL?mid=$mid&season_id=$id&sort_reverse=false&page_num=$page&page_size=30"
                BilibiliPlaylistKind.SERIES ->
                    "$SERIES_ARCHIVES_URL?mid=$mid&series_id=$id&only_normal=true&sort=desc&pn=$page&ps=30"
            }
        val body = request(mid, url)
        val data = decode<PlaylistArchivesResponse>(body).data ?: return BilibiliPlaylistVideos(emptyList(), 0)
        return BilibiliPlaylistVideos(
            videos = toVideos(data.archives.orEmpty()),
            total = data.page.total.takeIf { it > 0 } ?: data.meta.total,
        )
    }

    private suspend fun videosFrom(
        mid: Long,
        url: String,
        items: (UserVideosResponse) -> List<UserVideosResponse.Item>?,
    ): BilibiliChannelVideosPage {
        val body = request(mid, url)
        val videos = toVideos(items(decode<UserVideosResponse>(body)).orEmpty())
        return BilibiliChannelVideosPage(videos, hasMore = videos.isNotEmpty(), lastAid = videos.lastOrNull()?.aid ?: 0L)
    }

    private fun toVideos(items: List<UserVideosResponse.Item>): List<BilibiliChannelVideo> =
        items.mapNotNull { item ->
            val aid = item.aid.takeIf { it > 0 } ?: item.param.toLongOrNull() ?: 0L
            val bvid = BilibiliSigning.bvidOf(item.bvid, aid) ?: return@mapNotNull null
            BilibiliChannelVideo(
                bvid = bvid,
                aid = aid.takeIf { it > 0 } ?: BilibiliSigning.bv2av(bvid),
                title = item.title,
                thumbnailUrl = item.pic.ifEmpty { item.cover }.toHttps(),
                durationSec = item.duration.takeIf { it > 0 } ?: BilibiliSearchParser.parseDuration(item.length),
                viewCount = item.play.takeIf { it > 0 } ?: item.stat.view,
                uploadTimeSec = item.created.takeIf { it > 0 } ?: item.pubdate.takeIf { it > 0 } ?: item.ctime,
                authorName = item.author,
            )
        }

    private fun toRef(
        kind: BilibiliPlaylistKind,
        meta: PlaylistListResponse.Meta,
    ) = BilibiliPlaylistRef(
        kind = kind,
        mid = meta.mid,
        id = if (kind == BilibiliPlaylistKind.SEASON) meta.seasonId else meta.seriesId,
        name = meta.name,
        coverUrl = meta.cover.toHttps(),
        videoCount = meta.total,
    )

    private suspend fun webUrl(
        mid: Long,
        page: Int,
    ): String {
        val params = LinkedHashMap<String, String>()
        params["mid"] = mid.toString()
        params["order"] = "pubdate"
        params["ps"] = "25"
        params["pn"] = page.toString()
        params["order_avoided"] = "true"
        params["platform"] = "web"
        params["web_location"] = "333.1387"
        params.putAll(BilibiliSigning.dmImgParams())
        return session.signedUrl(WEB_VIDEOS_URL, params)
    }

    private suspend fun searchUrl(
        mid: Long,
        page: Int,
    ): String {
        val params = LinkedHashMap<String, String>()
        params["mid"] = mid.toString()
        params["keywords"] = ""
        params["order"] = "pubdate"
        params["pn"] = page.toString()
        params["ps"] = "20"
        params.putAll(BilibiliSigning.dmImgParams())
        return session.signedUrl(SEARCH_VIDEOS_URL, params)
    }

    /** The app API pages by the last video's aid instead of a page number. */
    private fun appUrl(
        mid: Long,
        lastAid: Long,
    ): String {
        val params = LinkedHashMap<String, String>()
        params["vmid"] = mid.toString()
        if (lastAid > 0) params["aid"] = lastAid.toString()
        params["order"] = "pubdate"
        params["mobi_app"] = "android"
        params["ts"] = (System.currentTimeMillis() / 1000).toString()
        return "$APP_VIDEOS_URL?${BilibiliSigning.signApp(params)}"
    }

    private suspend fun request(
        mid: Long,
        url: String,
    ): String = session.guardedGet(json, "https://space.bilibili.com/$mid") { url }

    private inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString<T>(body)
        } catch (e: SerializationException) {
            throw BilibiliContentNotAvailableException("Unreadable Bilibili response: ${e.message}")
        }

    private companion object {
        const val TAG = "BilibiliUserSpace"
        const val MODE_WEB = 0
        const val MODE_SEARCH = 1
        const val VIDEO_API_MODES = 3

        const val CARD_URL = "https://api.bilibili.com/x/web-interface/card?photo=true&mid="
        const val WEB_VIDEOS_URL = "https://api.bilibili.com/x/space/wbi/arc/search"
        const val SEARCH_VIDEOS_URL = "https://api.bilibili.com/x/series/recArchivesByKeywords"
        const val APP_VIDEOS_URL = "https://app.bilibili.com/x/v2/space/archive/cursor"
        const val SEASONS_SERIES_URL = "https://api.bilibili.com/x/polymer/web-space/seasons_series_list"
        const val SEASON_ARCHIVES_URL = "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list"
        const val SERIES_ARCHIVES_URL = "https://api.bilibili.com/x/series/archives"
    }
}
