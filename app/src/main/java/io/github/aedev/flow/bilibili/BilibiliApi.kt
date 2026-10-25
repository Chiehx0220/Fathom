package io.github.aedev.flow.bilibili

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Bilibili web API facade (InnerTube counterpart): VOD, live; no bangumi, no paid content.
 *
 * @param session cookies, forged device, WBI key.
 * @param loggedInCookie user cookie; anonymous caps 1080p.
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
    private val videos = BilibiliVideos(session, loggedInCookie, json)
    private val search = BilibiliSearch(session, json)
    private val userSpace = BilibiliUserSpace(session, json)
    private val comments = BilibiliComments(session, json)
    private val live = BilibiliLive(session, json)
    private val liveChat = BilibiliLiveChat(session, live, json)

    // region Video

    /** 1-based part (`?p=`). */
    suspend fun videoInfo(
        bvid: String,
        page: Int = 1,
    ): BilibiliVideoInfo = videos.videoInfo(bvid, page)

    /** Metadata + playable streams of one part. */
    suspend fun playback(
        bvid: String,
        page: Int = 1,
    ): BilibiliPlayback = videos.playback(bvid, page)

    /** Chapter marks of one part; empty if none. */
    suspend fun chapters(info: BilibiliVideoInfo): List<BilibiliChapter> = videos.chapters(info)

    /** Danmaku of one part; live chat: [liveMessages]. */
    suspend fun danmaku(info: BilibiliVideoInfo): List<BilibiliDanmaku> = videos.danmaku(info)

    /** Empty on error code. */
    suspend fun related(bvid: String): List<BilibiliRelated> = videos.related(bvid)

    /** Popular list, 1-based page, 20/page; empty on error code. */
    suspend fun popular(page: Int = 1): List<BilibiliRelated> = videos.popular(page)

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

    suspend fun search(
        keyword: String,
        type: BilibiliSearchType,
        page: Int,
    ): BilibiliSearchPage = search.search(keyword, type, page)

    // endregion

    // region Live

    /** Room + playback; throws [BilibiliLiveNotStartedException] (offline), [BilibiliLiveRebroadcastException] (replay). */
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

    /** Avatar missing in room info; channel lookup, optional. */
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
}
