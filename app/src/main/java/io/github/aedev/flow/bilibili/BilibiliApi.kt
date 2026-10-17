/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * services/bilibili/extractors/BillibiliStreamExtractor.java (video, related videos) and
 * BilibiliSearchExtractor.java. Chapters use the web player's own info endpoint and are not in PipePipe.
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * Bilibili web API (InnerTube counterpart): VOD, live. No bangumi, no paid content.
 *
 * @param session cookies, forged device, WBI key.
 * @param loggedInCookie user cookie; anonymous caps at 1080p.
 */
class BilibiliApi(
    private val session: BilibiliSession,
    private val loggedInCookie: () -> String? = { null },
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        },
) {
    private val userSpace = BilibiliUserSpace(session, json)
    private val comments = BilibiliComments(session, json)
    private val live = BilibiliLive(session, json)
    private val liveChat = BilibiliLiveChat(session, live, json)

    // region Video

    /** 1-based part (`?p=`). */
    suspend fun videoInfo(
        bvid: String,
        page: Int = 1,
    ): BilibiliVideoInfo {
        // PipePipe: empty Cookie header.
        val body = session.get("$VIEW_URL?bvid=$bvid", mapOf("Cookie" to ""))
        val response = json.decodeFromString<ViewResponse>(body)
        val data = response.data
        if (response.code != 0 || data == null) {
            throw BilibiliContentNotAvailableException(response.message.ifBlank { "Could not get Bilibili video metadata" })
        }
        val part = data.pages.getOrNull(page - 1) ?: throw BilibiliContentNotAvailableException("Part $page does not exist")
        return BilibiliVideoInfo(
            bvid = bvid,
            aid = data.aid,
            cid = part.cid,
            title = data.title,
            description = data.desc,
            thumbnailUrl = data.pic.toHttps(),
            durationSec = part.duration,
            uploader = BilibiliUploader(data.owner.mid, data.owner.name, data.owner.face.toHttps()),
            viewCount = data.stat.view,
            likeCount = data.stat.like,
            uploadTimeSec = data.pubdate,
            pages = data.pages.map { BilibiliPage(it.cid, it.page, it.part, it.duration) },
            isPaid = data.rights.pay == 1,
            category = data.tname,
        )
    }

    /** Metadata + playable streams of one part. */
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

        val response = json.decodeFromString<PlayUrlResponse>(session.get(session.signedUrl(PLAYURL_URL, params), headers))
        if (response.code != 0) {
            val message = response.message
            if (message.contains("地区")) throw BilibiliGeoRestrictedException(message)
            throw BilibiliContentNotAvailableException(message.ifBlank { "Bilibili code ${response.code}" })
        }
        val dash = response.data?.dash
        if (dash == null || (dash.video.isNullOrEmpty() && dash.audio.isNullOrEmpty())) {
            throw BilibiliPaidContentException("Paid content")
        }

        // Audio order (PipePipe): FLAC, Dolby, regular.
        val audioItems = listOfNotNull(dash.flac?.audio) + dash.dolby?.audio.orEmpty() + dash.audio.orEmpty()
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

    /** Chapter marks of one part; empty if none. */
    suspend fun chapters(info: BilibiliVideoInfo): List<BilibiliChapter> {
        val params = linkedMapOf("aid" to info.aid.toString(), "cid" to info.cid.toString())
        val headers = session.headers("https://www.bilibili.com/video/${info.bvid}")
        loggedInCookie()?.let { headers["Cookie"] = it }
        val response = json.decodeFromString<PlayerV2Response>(session.get(session.signedUrl(PLAYER_V2_URL, params), headers))
        return response.data
            ?.viewPoints
            .orEmpty()
            .filter { it.content.isNotBlank() }
            .map { BilibiliChapter(it.content, it.from.toInt(), it.imgUrl.takeIf { url -> url.isNotBlank() }) }
    }

    /** Danmaku of one part; live chat: [liveMessages]. */
    suspend fun danmaku(info: BilibiliVideoInfo): List<BilibiliDanmaku> {
        val headers = session.headers("https://www.bilibili.com/video/${info.bvid}")
        val raw = session.getBytes("$DANMAKU_URL${info.cid}", headers)
        return BilibiliDanmakuParser.parse(BilibiliDanmakuParser.decodeBody(raw))
    }

    /** Empty on error code. */
    suspend fun related(bvid: String): List<BilibiliRelated> {
        val headers = session.headers("https://www.bilibili.com/video/$bvid")
        val response = json.decodeFromString<RelatedResponse>(session.get("$RELATED_URL$bvid", headers))
        if (response.code != 0) return emptyList()
        return response.data.orEmpty().mapNotNull { it.toRelated() }
    }

    /** Popular list, 1-based page, 20/page; empty on error code. */
    suspend fun popular(page: Int = 1): List<BilibiliRelated> {
        val headers = session.headers("https://www.bilibili.com/v/popular/all")
        val response = json.decodeFromString<PopularResponse>(session.get("$POPULAR_URL?ps=20&pn=$page", headers))
        if (response.code != 0) return emptyList()
        return response.data
            ?.list
            .orEmpty()
            .mapNotNull { it.toRelated() }
    }

    private fun RelatedResponse.Item.toRelated(): BilibiliRelated? {
        val id = BilibiliSigning.bvidOf(bvid, aid) ?: return null
        return BilibiliRelated(
            bvid = id,
            title = title,
            thumbnailUrl = pic.toHttps(),
            durationSec = duration,
            viewCount = stat.view,
            uploader = BilibiliUploader(owner.mid, owner.name, owner.face.toHttps()),
            uploadTimeSec = pubdate,
            category = tname,
        )
    }

    private fun PlayUrlResponse.DashItem.toFormat(): BilibiliStreamFormat? {
        if (baseUrl.isEmpty()) return null
        val urls = BilibiliCdn.stableUrls(baseUrl, backupUrl.orEmpty())
        return BilibiliStreamFormat(
            id = id,
            url = urls.first(),
            backupUrls = urls.drop(1),
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

    // endregion

    // region Comments

    /** [offset]: empty = first page, else previous `nextOffset`. */
    suspend fun comments(
        bvid: String,
        offset: String = "",
    ): BilibiliCommentsPage = comments.page(bvid, offset)

    /** Replies to [rpid]; 1-based page, 10/page. */
    suspend fun commentReplies(
        bvid: String,
        rpid: String,
        page: Int,
    ): BilibiliRepliesPage = comments.replies(bvid, rpid, page)

    // endregion

    // region Search

    /** 1-based page; anime/films skipped. */
    suspend fun search(
        keyword: String,
        type: BilibiliSearchType,
        page: Int,
    ): BilibiliSearchPage {
        val headers = session.headers("https://www.bilibili.com/")
        val encoded = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
        val url = "$SEARCH_URL?search_type=${type.apiValue}&keyword=$encoded&page=$page"
        val response = json.decodeFromString<SearchResponse>(session.get(url, headers))
        if (response.code != 0) throw BilibiliContentNotAvailableException("Bilibili search code ${response.code}")
        val data = response.data ?: return BilibiliSearchPage(emptyList(), false)
        val items = data.result.orEmpty().mapNotNull { BilibiliSearchParser.toItem(it) }
        return BilibiliSearchPage(items, hasMore = page < data.numPages && !data.result.isNullOrEmpty())
    }

    // endregion

    // region Live

    /** Room + playback. Throws [BilibiliLiveNotStartedException] (offline), [BilibiliLiveRebroadcastException] (replay). */
    suspend fun livePlayback(roomId: Long): BilibiliLivePlayback {
        val room = withAvatar(live.room(roomId))
        when (room.status) {
            BilibiliLiveStatus.OFFLINE -> throw BilibiliLiveNotStartedException("Live is not started")
            BilibiliLiveStatus.REBROADCAST -> throw BilibiliLiveRebroadcastException("This room is replaying uploaded videos")
            BilibiliLiveStatus.LIVE -> Unit
        }
        val streams = live.streams(room.roomId)
        if (streams.hlsMasterUrl == null && streams.flvUrl == null) {
            throw BilibiliContentNotAvailableException("The room publishes no playable stream")
        }
        return BilibiliLivePlayback(room, streams)
    }

    suspend fun liveRoom(roomId: Long): BilibiliLiveRoom = withAvatar(live.room(roomId))

    /** Room info lacks uploader avatar; fetched from channel, optional. */
    private suspend fun withAvatar(room: BilibiliLiveRoom): BilibiliLiveRoom {
        if (room.uploader.avatarUrl.isNotBlank() || room.uploader.mid <= 0) return room
        val avatar =
            try {
                channelInfo(room.uploader.mid).avatarUrl
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return room
            }
        return room.copy(uploader = room.uploader.copy(avatarUrl = avatar))
    }

    suspend fun recommendedLives(): List<BilibiliLiveItem> = live.recommended()

    /** Room [mid] is live in; null if offline/no room. */
    suspend fun channelLiveRoom(mid: Long): BilibiliLiveItem? =
        try {
            live.roomOf(mid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /** Room chat; auto-reconnect until collector stops. [roomId]: long room number. */
    fun liveMessages(roomId: Long): Flow<BilibiliLiveMessage> = liveChat.messages(roomId)

    // endregion

    // region Uploader space

    suspend fun channelInfo(mid: Long): BilibiliChannelInfo = userSpace.info(mid)

    suspend fun channelVideos(
        mid: Long,
        key: BilibiliChannelPageKey,
    ): BilibiliChannelVideosPage = userSpace.videos(mid, key)

    suspend fun channelPlaylists(
        mid: Long,
        page: Int,
    ): BilibiliPlaylistPage = userSpace.playlists(mid, page)

    /** Series/season page, 1-based, 30/page. */
    suspend fun playlistVideos(
        kind: BilibiliPlaylistKind,
        mid: Long,
        id: Long,
        page: Int,
    ): BilibiliPlaylistVideos = userSpace.playlistVideos(kind, mid, id, page)

    // endregion

    private companion object {
        const val VIEW_URL = "https://api.bilibili.com/x/web-interface/wbi/view"
        const val PLAYURL_URL = "https://api.bilibili.com/x/player/wbi/playurl"
        const val PLAYER_V2_URL = "https://api.bilibili.com/x/player/wbi/v2"
        const val DANMAKU_URL = "https://api.bilibili.com/x/v1/dm/list.so?oid="
        const val RELATED_URL = "https://api.bilibili.com/x/web-interface/archive/related?bvid="
        const val POPULAR_URL = "https://api.bilibili.com/x/web-interface/popular"
        const val SEARCH_URL = "https://api.bilibili.com/x/web-interface/search/type"
    }
}
