/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * extractor/src/main/java/org/schabi/newpipe/extractor/services/bilibili/extractors/BillibiliStreamExtractor.java
 * (onFetchPage, non-live and non-bangumi branch; buildVideoOnlyStreamsArray / getAudioStreams)
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.json.Json

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
        const val VIEW_URL = "https://api.bilibili.com/x/web-interface/wbi/view"
        const val PLAYER_V2_URL = "https://api.bilibili.com/x/player/wbi/v2"
        const val SEARCH_URL = "https://api.bilibili.com/x/web-interface/search/type"
        const val RELATED_URL = "https://api.bilibili.com/x/web-interface/archive/related?bvid="
        const val DANMAKU_URL = "https://api.bilibili.com/x/v1/dm/list.so?oid="
        const val PLAYURL_URL = "https://api.bilibili.com/x/player/wbi/playurl"
    }
}
