/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * extractor/src/main/java/org/schabi/newpipe/extractor/services/bilibili/extractors/BillibiliStreamExtractor.java
 * (onFetchPage, non-live and non-bangumi branch; buildVideoOnlyStreamsArray / getAudioStreams)
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bilibili's web API, in the same role InnerTube plays for YouTube: one method per endpoint plus the
 * orchestration a playback needs. Regular UGC videos only for now; bangumi/premium, live and the
 * app-signed endpoints are not ported yet.
 *
 * @param session owns the cookies, device headers and WBI key, and is shared across calls.
 * @param loggedInCookie the user's own Bilibili cookie string, if they signed in. Only then does
 *   Bilibili hand out 1080p+ and skip the try_look trial mode.
 */
class BilibiliApi(
    private val session: BilibiliSession,
    private val loggedInCookie: () -> String? = { null },
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    private val videoApiMode = AtomicInteger(MODE_WEB)

    /** Metadata for [bvid]; [page] is the 1-based part, as in the "?p=" URL parameter. */
    suspend fun videoInfo(
        bvid: String,
        page: Int = 1,
    ): BilibiliVideoInfo {
        // PipePipe sends this one request with an empty Cookie header and nothing else.
        val body = session.get("$VIEW_URL?bvid=$bvid", mapOf("Cookie" to ""))
        val response = json.decodeFromString<ViewResponse>(body)
        val data = response.data
        if (response.code != 0 || data == null) {
            throw BilibiliContentNotAvailableException(
                response.message.ifBlank { "Could not get Bilibili video metadata" },
            )
        }
        val part =
            data.pages.getOrNull(page - 1)
                ?: throw BilibiliContentNotAvailableException("Part $page does not exist")
        return BilibiliVideoInfo(
            bvid = bvid,
            aid = data.aid,
            cid = part.cid,
            title = data.title,
            description = data.desc,
            thumbnailUrl = data.pic.replace("http:", "https:"),
            durationSec = part.duration,
            uploader = BilibiliUploader(data.owner.mid, data.owner.name, data.owner.face.replace("http:", "https:")),
            viewCount = data.stat.view,
            likeCount = data.stat.like,
            uploadTimeSec = data.pubdate,
            pages = data.pages.map { BilibiliPage(it.cid, it.page, it.part, it.duration) },
            isPaid = data.rights.pay == 1,
        )
    }

    /** Metadata plus the playable streams for one part of [bvid]. */
    suspend fun playback(
        bvid: String,
        page: Int = 1,
    ): BilibiliPlayback {
        val info = videoInfo(bvid, page)
        val originalUrl = "https://www.bilibili.com/video/$bvid"

        val params = LinkedHashMap<String, String>()
        params["avid"] = BilibiliSigning.bv2av(bvid).toString()
        params["bvid"] = bvid
        params["cid"] = info.cid.toString()
        params["qn"] = "120"
        params["fnver"] = "0"
        params["fnval"] = "4048"
        params["fourk"] = "1"

        val headers = session.headers(originalUrl)
        val cookie = loggedInCookie()
        if (cookie != null) {
            headers["Cookie"] = cookie
        } else {
            // https://codeberg.org/NullPointerException/PipePipe/issues/42
            params["try_look"] = "1"
        }
        params["web_location"] = "1315873"
        params.putAll(BilibiliSigning.dmImgParams())

        val body = session.get(session.signedUrl(PLAYURL_URL, params), headers)
        val response = json.decodeFromString<PlayUrlResponse>(body)
        if (response.code != 0) {
            val message = response.message
            if (message.contains("地区")) throw BilibiliGeoRestrictedException(message)
            throw BilibiliContentNotAvailableException(message.ifBlank { "Bilibili code ${response.code}" })
        }
        val dash = response.data?.dash
        if (dash == null || (dash.video.isNullOrEmpty() && dash.audio.isNullOrEmpty())) {
            throw BilibiliPaidContentException("Paid content")
        }

        // Highest-quality audio first: FLAC, then Dolby, then the regular tracks, as PipePipe orders them.
        val audioItems =
            listOfNotNull(dash.flac?.audio) + (dash.dolby?.audio.orEmpty()) + dash.audio.orEmpty()

        val playback =
            BilibiliPlayback(
                info = info,
                videoFormats = dash.video.orEmpty().mapNotNull { it.toFormat() },
                audioFormats = audioItems.mapNotNull { it.toFormat() },
                requestHeaders = session.userAgentHeaders(originalUrl),
            )
        if (info.isPaid && playback.videoFormats.isEmpty() && playback.audioFormats.isEmpty()) {
            throw BilibiliPaidContentException("Paid content")
        }
        return playback
    }

    /**
     * The uploader's chapter marks for [info]'s part; empty when there are none. Not part of
     * PipePipeExtractor: it reads the web player's own info endpoint, so PPE diffs do not cover it.
     */
    suspend fun chapters(info: BilibiliVideoInfo): List<BilibiliChapter> {
        val params = linkedMapOf("aid" to info.aid.toString(), "cid" to info.cid.toString())
        val headers = session.headers("https://www.bilibili.com/video/${info.bvid}")
        loggedInCookie()?.let { headers["Cookie"] = it }
        val body = session.get(session.signedUrl(PLAYER_V2_URL, params), headers)
        val response = json.decodeFromString<PlayerV2Response>(body)
        return response.data?.viewPoints.orEmpty()
            .filter { it.content.isNotBlank() }
            .map { BilibiliChapter(it.content, it.from.toInt(), it.imgUrl.takeIf { url -> url.isNotBlank() }) }
    }

    /** An uploader's profile, from the same `web-interface/card` PipePipe's channel extractor reads. */
    suspend fun channelInfo(mid: Long): BilibiliChannelInfo {
        val body = userSpaceRequest(mid) { headers -> session.getLenient("$USER_CARD_URL$mid", headers) }
        val data =
            json.decodeFromString<CardResponse>(body).data
                ?: throw BilibiliContentNotAvailableException("Channel $mid not found")
        return BilibiliChannelInfo(
            mid = mid,
            name = data.card.name,
            avatarUrl = data.card.face.replace("http:", "https:"),
            bannerUrl = data.space?.banner?.takeIf { it.isNotBlank() }?.replace("http:", "https:"),
            followerCount = data.card.fans,
            description = data.card.sign,
        )
    }

    /**
     * One page of an uploader's videos, newest first, through whichever of PipePipe's three list
     * APIs is currently getting through: web, keyword search, then the app's cursor API. A blocked
     * request moves to the next mode and the choice is remembered for the next call, as PipePipe
     * does with its rotating mode, so a network Bilibili distrusts stops paying for the failures.
     */
    suspend fun channelVideos(
        mid: Long,
        key: BilibiliChannelPageKey,
    ): BilibiliChannelVideosPage {
        var failure: BilibiliException? = null
        repeat(VIDEO_API_MODES) {
            val mode = videoApiMode.get()
            try {
                return when (mode) {
                    MODE_WEB -> channelVideosFrom(mid, webVideosUrl(mid, key.page)) { it.data?.list?.vlist }
                    MODE_SEARCH -> channelVideosFrom(mid, searchVideosUrl(mid, key.page)) { it.data?.archives }
                    else -> channelVideosFrom(mid, clientVideosUrl(mid, key.lastAid)) { it.data?.item }
                }
            } catch (e: BilibiliException) {
                failure = e
                Log.w("BilibiliApi", "Channel video mode $mode blocked: ${e.message?.take(80)}")
                videoApiMode.compareAndSet(mode, (mode + 1) % VIDEO_API_MODES)
            }
        }
        throw failure ?: BilibiliContentNotAvailableException("Bilibili blocked every channel video API")
    }

    private suspend fun channelVideosFrom(
        mid: Long,
        url: String,
        items: (UserVideosResponse) -> List<UserVideosResponse.Item>?,
    ): BilibiliChannelVideosPage {
        val body = userSpaceRequest(mid) { headers -> session.getLenient(url, headers) }
        val response =
            try {
                json.decodeFromString<UserVideosResponse>(body)
            } catch (e: SerializationException) {
                throw BilibiliContentNotAvailableException("Unreadable channel video list: ${e.message}")
            }
        val videos = toChannelVideos(items(response).orEmpty())
        return BilibiliChannelVideosPage(videos, hasMore = videos.isNotEmpty(), lastAid = videos.lastOrNull()?.aid ?: 0L)
    }

    private fun toChannelVideos(items: List<UserVideosResponse.Item>): List<BilibiliChannelVideo> =
        items.mapNotNull { item ->
            val aid = item.aid.takeIf { it > 0 } ?: item.param.toLongOrNull() ?: 0L
            val id = item.bvid.ifEmpty { if (aid > 0) BilibiliSigning.av2bv(aid) else "" }
            if (id.isEmpty()) return@mapNotNull null
            BilibiliChannelVideo(
                bvid = id,
                aid = aid.takeIf { it > 0 } ?: BilibiliSigning.bv2av(id),
                title = item.title,
                thumbnailUrl = item.pic.ifEmpty { item.cover }.replace("http:", "https:"),
                durationSec = item.duration.takeIf { it > 0 } ?: BilibiliSearchParser.parseDuration(item.length),
                viewCount = item.play.takeIf { it > 0 } ?: item.stat.view,
                uploadTimeSec = item.created.takeIf { it > 0 } ?: item.pubdate.takeIf { it > 0 } ?: item.ctime,
                authorName = item.author,
            )
        }

    /**
     * The uploader's series and seasons, one page (1-based) at a time, as PipePipe's channel tab
     * extractor reads `seasons_series_list`. Lists with no videos are skipped, as it does.
     */
    suspend fun channelPlaylists(
        mid: Long,
        page: Int,
    ): BilibiliPlaylistPage {
        val url = "$SEASONS_SERIES_LIST_URL?mid=$mid&page_num=$page&page_size=10"
        val body = userSpaceRequest(mid) { headers -> session.getLenient(url, headers) }
        val lists = decodeOrBlocked<PlaylistListResponse>(body).data?.itemsLists ?: PlaylistListResponse.Lists()
        val refs =
            lists.seasons.orEmpty().map { toRef(BilibiliPlaylistKind.SEASON, it.meta) } +
                lists.series.orEmpty().map { toRef(BilibiliPlaylistKind.SERIES, it.meta) }
        val nonEmpty = refs.filter { it.videoCount > 0 }
        return BilibiliPlaylistPage(nonEmpty, hasMore = refs.isNotEmpty())
    }

    private fun toRef(
        kind: BilibiliPlaylistKind,
        meta: PlaylistListResponse.Meta,
    ) = BilibiliPlaylistRef(
        kind = kind,
        mid = meta.mid,
        id = if (kind == BilibiliPlaylistKind.SEASON) meta.seasonId else meta.seriesId,
        name = meta.name,
        coverUrl = meta.cover.replace("http:", "https:"),
        videoCount = meta.total,
    )

    /** One page (1-based, 30 per page) of the videos in a series or season. */
    suspend fun playlistVideos(
        kind: BilibiliPlaylistKind,
        mid: Long,
        id: Long,
        page: Int,
    ): BilibiliPlaylistVideos {
        val url =
            if (kind == BilibiliPlaylistKind.SEASON) {
                "$SEASON_ARCHIVES_URL?mid=$mid&season_id=$id&sort_reverse=false&page_num=$page&page_size=30"
            } else {
                "$SERIES_ARCHIVES_URL?mid=$mid&series_id=$id&only_normal=true&sort=desc&pn=$page&ps=30"
            }
        val body = userSpaceRequest(mid) { headers -> session.getLenient(url, headers) }
        val data = decodeOrBlocked<PlaylistArchivesResponse>(body).data ?: return BilibiliPlaylistVideos(emptyList(), 0)
        return BilibiliPlaylistVideos(
            videos = toChannelVideos(data.archives.orEmpty()),
            total = data.page.total.takeIf { it > 0 } ?: data.meta.total,
        )
    }

    private inline fun <reified T> decodeOrBlocked(body: String): T =
        try {
            json.decodeFromString<T>(body)
        } catch (e: SerializationException) {
            throw BilibiliContentNotAvailableException("Unreadable Bilibili response: ${e.message}")
        }

    private suspend fun webVideosUrl(
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
        return session.signedUrl(USER_VIDEOS_WEB_URL, params)
    }

    private suspend fun searchVideosUrl(
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
        return session.signedUrl(USER_VIDEOS_SEARCH_URL, params)
    }

    /** The app API is signed with the Android client's key and pages by the last video's aid. */
    private fun clientVideosUrl(
        mid: Long,
        lastAid: Long,
    ): String {
        val params = LinkedHashMap<String, String>()
        params["vmid"] = mid.toString()
        if (lastAid > 0) params["aid"] = lastAid.toString()
        params["order"] = "pubdate"
        params["mobi_app"] = "android"
        params["ts"] = (System.currentTimeMillis() / 1000).toString()
        return "$USER_VIDEOS_CLIENT_URL?${BilibiliSigning.signApp(params)}"
    }

    /**
     * PipePipe's requestUserSpaceResponse: up to two tries, and on an HTML page (risk control) or
     * code -352 a fresh device and cookie set before the next one. The body is read whatever the
     * HTTP status, because a block arrives as a 412 carrying an HTML page.
     */
    private suspend fun userSpaceRequest(
        mid: Long,
        fetch: suspend (Map<String, String>) -> String,
    ): String {
        var last = ""
        repeat(2) {
            val headers = session.headers("https://space.bilibili.com/$mid")
            last = fetch(headers)
            if (!last.trimStart().startsWith("{")) {
                session.reset()
                return@repeat
            }
            val code =
                try {
                    json.decodeFromString<CodeOnly>(last).code
                } catch (e: SerializationException) {
                    -1
                }
            if (code == 0) return last
            if (code == -352) session.reset()
        }
        throw BilibiliContentNotAvailableException("Bilibili blocked the request: ${last.take(120)}")
    }

    /**
     * One page (1-based) of search results, as PipePipe's search extractor reads
     * `search/type`: a plain GET carrying the anonymous cookies, no WBI signature. Live rooms,
     * anime and film results are not read yet, so those rows are skipped.
     */
    suspend fun search(
        keyword: String,
        type: BilibiliSearchType,
        page: Int,
    ): BilibiliSearchPage {
        val headers = session.headers("https://www.bilibili.com/")
        val url =
            "$SEARCH_URL?search_type=${type.apiValue}&keyword=${java.net.URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")}&page=$page"
        val response = json.decodeFromString<SearchResponse>(session.get(url, headers))
        if (response.code != 0) {
            throw BilibiliContentNotAvailableException("Bilibili search code ${response.code}")
        }
        val data = response.data ?: return BilibiliSearchPage(emptyList(), false)
        val items = data.result.orEmpty().mapNotNull { BilibiliSearchParser.toItem(it) }
        return BilibiliSearchPage(items, hasMore = page < data.numPages && !data.result.isNullOrEmpty())
    }

    /**
     * The related-videos lane for [bvid], as PipePipe reads it (`archive/related`). Empty on any
     * non-zero code: a missing lane must never fail the playback it sits beside.
     */
    suspend fun related(bvid: String): List<BilibiliRelated> {
        val headers = session.headers("https://www.bilibili.com/video/$bvid")
        val response = json.decodeFromString<RelatedResponse>(session.get("$RELATED_URL$bvid", headers))
        if (response.code != 0) return emptyList()
        return response.data.orEmpty().mapNotNull { item ->
            // PipePipe falls back to the av number when a row carries no bvid.
            val id = item.bvid.ifEmpty { if (item.aid > 0) BilibiliSigning.av2bv(item.aid) else "" }
            if (id.isEmpty()) return@mapNotNull null
            BilibiliRelated(
                bvid = id,
                title = item.title,
                thumbnailUrl = item.pic.replace("http:", "https:"),
                durationSec = item.duration,
                viewCount = item.stat.view,
                uploader = BilibiliUploader(item.owner.mid, item.owner.name, item.owner.face.replace("http:", "https:")),
                uploadTimeSec = item.pubdate,
            )
        }
    }

    /**
     * Every danmaku of [info]'s part (the VOD list, not live). Ported from PipePipe's bullet-comment
     * extractor; the endpoint takes the part's cid.
     */
    suspend fun danmaku(info: BilibiliVideoInfo): List<BilibiliDanmaku> {
        val headers = session.headers("https://www.bilibili.com/video/${info.bvid}")
        val raw = session.getBytes("$DANMAKU_URL${info.cid}", headers)
        return BilibiliDanmakuParser.parse(BilibiliDanmakuParser.decodeBody(raw))
    }

    private fun PlayUrlResponse.DashItem.toFormat(): BilibiliStreamFormat? {
        if (baseUrl.isEmpty()) return null
        return BilibiliStreamFormat(
            id = id,
            url = baseUrl,
            backupUrls = backupUrl.orEmpty(),
            codecs = codecs,
            bandwidth = bandwidth,
            width = width,
            height = height,
            frameRate = frameRate,
            initRange = segmentBase?.initialization?.toRange(),
            indexRange = segmentBase?.indexRange?.toRange(),
        )
    }

    private fun String.toRange(): LongRange? {
        val parts = split("-")
        if (parts.size != 2) return null
        val start = parts[0].toLongOrNull() ?: return null
        val end = parts[1].toLongOrNull() ?: return null
        return start..end
    }

    companion object {
        private const val MODE_WEB = 0
        private const val MODE_SEARCH = 1
        private const val VIDEO_API_MODES = 3

        const val VIEW_URL = "https://api.bilibili.com/x/web-interface/wbi/view"
        const val PLAYER_V2_URL = "https://api.bilibili.com/x/player/wbi/v2"
        const val USER_CARD_URL = "https://api.bilibili.com/x/web-interface/card?photo=true&mid="
        const val USER_VIDEOS_WEB_URL = "https://api.bilibili.com/x/space/wbi/arc/search"
        const val USER_VIDEOS_CLIENT_URL = "https://app.bilibili.com/x/v2/space/archive/cursor"
        const val SEASONS_SERIES_LIST_URL = "https://api.bilibili.com/x/polymer/web-space/seasons_series_list"
        const val SEASON_ARCHIVES_URL = "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list"
        const val SERIES_ARCHIVES_URL = "https://api.bilibili.com/x/series/archives"
        const val USER_VIDEOS_SEARCH_URL = "https://api.bilibili.com/x/series/recArchivesByKeywords"
        const val SEARCH_URL = "https://api.bilibili.com/x/web-interface/search/type"
        const val RELATED_URL = "https://api.bilibili.com/x/web-interface/archive/related?bvid="
        const val DANMAKU_URL = "https://api.bilibili.com/x/v1/dm/list.so?oid="
        const val PLAYURL_URL = "https://api.bilibili.com/x/player/wbi/playurl"
    }
}
