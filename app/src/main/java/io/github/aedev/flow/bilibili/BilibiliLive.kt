/*
 * Ported from PipePipeExtractor (GPL-3.0), up to commit c68e10e2 (v5.4.0):
 * services/bilibili/extractors/BillibiliStreamExtractor.java (live branch: getRoomBaseInfo, getRoomPlayInfo),
 * BilibiliRecommendLiveInfoItemExtractor.java and BilibiliBulletCommentsExtractor.java (getDanmuInfo).
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Bilibili's live rooms: the room page, its stream addresses, the recommended list and the chat socket's access. */
internal class BilibiliLive(
    private val session: BilibiliSession,
    private val json: Json,
) {
    suspend fun room(roomId: Long): BilibiliLiveRoom {
        val headers = session.headers(liveReferer(roomId))
        val url = "$ROOM_BASE_INFO_URL$roomId&req_biz=web_room_componet"
        val response = json.decodeFromString<RoomBaseInfoResponse>(session.get(url, headers))
        val room = response.data?.byRoomIds?.get(roomId.toString())
        if (response.code != 0 || room == null || room.roomId <= 0) {
            throw BilibiliContentNotAvailableException(response.message.ifBlank { "Can not get live room info" })
        }
        return BilibiliLiveRoom(
            roomId = room.roomId,
            title = room.title,
            coverUrl = (room.coverFromUser ?: room.cover).toHttps(),
            // The room info carries no avatar; the channel lookup that follows fills it in.
            uploader = BilibiliUploader(room.uid, room.uname, room.face.orEmpty().toHttps()),
            viewerCount = room.online,
            startedAtSec = parseStartTime(room.liveTime),
            tags = (room.tagName + "," + room.tags).split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            status =
                when (room.liveStatus) {
                    1 -> BilibiliLiveStatus.LIVE
                    2 -> BilibiliLiveStatus.REBROADCAST
                    else -> BilibiliLiveStatus.OFFLINE
                },
        )
    }

    /**
     * One request answers both shapes: the fmp4 HLS entry's master playlist (every quality the viewer
     * may watch, so the player can adapt) and the FLV entry, which a few rooms (about 1 in 50) publish
     * instead. [roomId] must be the long room number from [room].
     */
    suspend fun streams(roomId: Long): BilibiliLiveStreams {
        val headers = session.headers(liveReferer(roomId))
        val url = "$ROOM_PLAY_INFO_URL?room_id=$roomId&protocol=0,1&format=0,1,2&codec=0,1&qn=10000&platform=web&dm_disabled=1"
        val response = json.decodeFromString<RoomPlayInfoResponse>(session.get(url, headers))
        if (response.code != 0) throw BilibiliContentNotAvailableException(response.message.ifBlank { "Bilibili code ${response.code}" })
        var hls: String? = null
        var flv: String? = null
        for (stream in response.data?.playurlInfo?.playurl?.stream.orEmpty()) {
            for (format in stream.format) {
                when {
                    stream.protocolName == "http_hls" && format.formatName == "fmp4" && hls == null -> {
                        hls = format.masterUrl?.takeIf { it.isNotEmpty() }
                    }

                    stream.protocolName == "http_stream" && format.formatName == "flv" && flv == null -> {
                        flv = flvUrl(format)
                    }
                }
            }
        }
        return BilibiliLiveStreams(hls, flv)
    }

    suspend fun recommended(): List<BilibiliLiveItem> {
        val headers = session.headers(LIVE_HOME)
        val response = json.decodeFromString<RecommendedLivesResponse>(session.get(RECOMMENDED_URL, headers))
        if (response.code != 0) return emptyList()
        return response.data.orEmpty().filter { it.roomid > 0 }.map {
            BilibiliLiveItem(
                roomId = it.roomid,
                title = it.title,
                coverUrl = it.userCover.ifBlank { it.systemCover }.toHttps(),
                uploaderName = it.uname,
                uploaderAvatarUrl = it.face.toHttps(),
                viewerCount = it.watchedShow?.num ?: 0,
            )
        }
    }

    /** The room [mid] is streaming from right now, if any (a rebroadcast does not count). */
    suspend fun roomOf(mid: Long): BilibiliLiveItem? {
        val headers = session.headers(LIVE_HOME)
        val response = json.decodeFromString<LiveStatusByUidsResponse>(session.get("$STATUS_BY_UIDS_URL$mid", headers))
        val room = response.data?.get(mid.toString())
        if (response.code != 0 || room == null || room.liveStatus != 1 || room.roomId <= 0) return null
        return BilibiliLiveItem(
            roomId = room.roomId,
            title = room.title,
            coverUrl = room.coverFromUser.toHttps(),
            uploaderName = room.uname,
            uploaderAvatarUrl = room.face.toHttps(),
            viewerCount = room.online,
        )
    }

    suspend fun chatAccess(roomId: Long): BilibiliLiveChatAccess {
        val headers = session.headers(liveReferer(roomId))
        val params = linkedMapOf("id" to roomId.toString(), "type" to "0")
        val response = json.decodeFromString<DanmuInfoResponse>(session.get(session.signedUrl(DANMU_INFO_URL, params), headers))
        val data = response.data
        if (response.code != 0 || data == null || data.token.isEmpty()) {
            throw BilibiliContentNotAvailableException("Could not get the live chat token")
        }
        val endpoints = data.hostList.filter { it.host.isNotEmpty() }.map { "wss://${it.host}:${it.wssPort}/sub" }
        return BilibiliLiveChatAccess(
            roomId = roomId,
            token = data.token,
            endpoints = endpoints.ifEmpty { listOf(FALLBACK_CHAT_ENDPOINT) },
            buvid = session.defaultCookies()["buvid3"],
        )
    }

    /** Rebuilds a stream address from one codec entry, preferring the avc ladder over hevc. */
    private fun flvUrl(format: RoomPlayInfoResponse.Format): String? {
        val urls =
            format.codec.mapNotNull { codec ->
                val mirror = codec.urlInfo.firstOrNull() ?: return@mapNotNull null
                if (codec.baseUrl.isEmpty()) return@mapNotNull null
                codec.codecName to (mirror.host + codec.baseUrl + mirror.extra)
            }
        return (urls.firstOrNull { it.first == "avc" } ?: urls.firstOrNull())?.second
    }

    /** "0000-00-00 00:00:00" (not live) and anything else unreadable is 0. */
    private fun parseStartTime(text: String?): Long {
        if (text.isNullOrEmpty()) return 0L
        return runCatching { LocalDateTime.parse(text, START_TIME).toEpochSecond(ZoneOffset.ofHours(8)) }
            .getOrDefault(0L)
            .coerceAtLeast(0L)
    }

    private fun liveReferer(roomId: Long) = "https://live.bilibili.com/$roomId"

    private companion object {
        const val ROOM_BASE_INFO_URL = "https://api.live.bilibili.com/xlive/web-room/v1/index/getRoomBaseInfo?room_ids="
        const val ROOM_PLAY_INFO_URL = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo"
        const val DANMU_INFO_URL = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo"
        const val STATUS_BY_UIDS_URL = "https://api.live.bilibili.com/room/v1/Room/get_status_info_by_uids?uids[]="
        const val RECOMMENDED_URL = "https://api.live.bilibili.com/room/v1/room/get_user_recommend?page_size=30&platform=web"
        const val FALLBACK_CHAT_ENDPOINT = "wss://broadcastlv.chat.bilibili.com/sub"
        const val LIVE_HOME = "https://live.bilibili.com/"
        val START_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
