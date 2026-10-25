package io.github.aedev.flow.bilibili

import android.app.Application
import io.github.aedev.flow.bilibili.fake.FakeBilibili
import io.github.aedev.flow.bilibili.fake.verifyGolden
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** Offline replay of [BilibiliApi]: results, request shapes. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class)
class BilibiliApiGoldenTest {
    private val bvid = "BV1GJ411x7h7"

    private fun scenario(
        name: String,
        setup: FakeBilibili.() -> Unit = {},
        loggedInCookie: String? = null,
        block: suspend (BilibiliApi) -> Any?,
    ) {
        val fake = FakeBilibili().apply(setup)
        val result =
            runBlocking {
                try {
                    block(fake.api(loggedInCookie))
                } catch (e: Exception) {
                    e
                }
            }
        verifyGolden(name, result, "#requests\n${fake.requestLog()}")
    }

    // region Video

    @Test
    fun videoInfo() = scenario("video-info", { serve(FakeBilibili.VIEW, "view.json") }) { it.videoInfo(bvid) }

    @Test
    fun videoInfoSecondPart() = scenario("video-info-p2", { serve(FakeBilibili.VIEW, "view.json") }) { it.videoInfo(bvid, 2) }

    @Test
    fun videoInfoMissingPart() = scenario("video-info-p9", { serve(FakeBilibili.VIEW, "view.json") }) { it.videoInfo(bvid, 9) }

    @Test
    fun videoInfoError() = scenario("video-info-error", { serve(FakeBilibili.VIEW, "view_error.json") }) { it.videoInfo(bvid) }

    // UA random per run
    private fun BilibiliPlayback.stable() = copy(requestHeaders = requestHeaders - "User-Agent")

    private fun FakeBilibili.playable() = serve(FakeBilibili.VIEW, "view.json").serve(FakeBilibili.PLAYURL, "playurl.json")

    @Test
    fun playback() = scenario("playback", { playable() }) { it.playback(bvid, 2).stable() }

    @Test
    fun playbackLoggedIn() = scenario("playback-logged-in", { playable() }, loggedInCookie = "SESSDATA=abc") { it.playback(bvid).stable() }

    @Test
    fun playbackGeoBlocked() =
        scenario(
            "playback-geo",
            { serve(FakeBilibili.VIEW, "view.json").serve(FakeBilibili.PLAYURL, "playurl_geo.json") },
        ) { it.playback(bvid) }

    @Test
    fun playbackWithoutStreams() =
        scenario("playback-empty", {
            serve(FakeBilibili.VIEW, "view.json").serve(FakeBilibili.PLAYURL, "playurl_empty.json")
        }) { it.playback(bvid) }

    @Test
    fun playbackOtherError() =
        scenario("playback-other", {
            serve(FakeBilibili.VIEW, "view.json").serve(FakeBilibili.PLAYURL, "playurl_other.json")
        }) { it.playback(bvid) }

    @Test
    fun chapters() =
        scenario("chapters", { serve(FakeBilibili.VIEW, "view.json").serve(FakeBilibili.PLAYER_V2, "player_v2.json") }) {
            it.chapters(it.videoInfo(bvid))
        }

    @Test
    fun danmaku() =
        scenario("danmaku", { serve(FakeBilibili.VIEW, "view.json").serve(FakeBilibili.DANMAKU, "danmaku.xml") }) {
            it.danmaku(it.videoInfo(bvid))
        }

    @Test
    fun related() = scenario("related", { serve(FakeBilibili.RELATED, "related.json") }) { it.related(bvid) }

    @Test
    fun popular() = scenario("popular", { serve(FakeBilibili.POPULAR, "popular.json") }) { it.popular(2) }

    // endregion

    // region Search

    @Test
    fun searchVideo() =
        scenario("search-video", {
            serve(FakeBilibili.SEARCH, "search_video.json")
        }) { it.search("cat dog", BilibiliSearchType.VIDEO, 1) }

    @Test
    fun searchLastPage() =
        scenario("search-video-last", {
            serve(FakeBilibili.SEARCH, "search_video.json")
        }) { it.search("猫", BilibiliSearchType.VIDEO, 3) }

    @Test
    fun searchUser() =
        scenario("search-user", {
            serve(FakeBilibili.SEARCH, "search_user.json")
        }) { it.search("u", BilibiliSearchType.USER, 1) }

    @Test
    fun searchLive() =
        scenario("search-live", {
            serve(FakeBilibili.SEARCH, "search_live.json")
        }) { it.search("l", BilibiliSearchType.LIVE, 1) }

    @Test
    fun searchError() =
        scenario("search-error", {
            serve(FakeBilibili.SEARCH, "search_error.json")
        }) { it.search("x", BilibiliSearchType.VIDEO, 1) }

    // endregion

    // region Comments

    @Test
    fun commentsFirstPage() = scenario("comments-first", { serve(FakeBilibili.COMMENTS, "comments.json") }) { it.comments(bvid) }

    @Test
    fun commentsNextPage() =
        scenario("comments-next", { serve(FakeBilibili.COMMENTS, "comments_last.json") }) { it.comments(bvid, "OFFSET-2") }

    @Test
    fun commentsClosed() = scenario("comments-closed", { serve(FakeBilibili.COMMENTS, "comments_closed.json") }) { it.comments(bvid) }

    @Test
    fun commentReplies() =
        scenario("comment-replies", {
            serve(FakeBilibili.REPLIES, "comment_replies.json")
        }) { it.commentReplies(bvid, "9001", 1) }

    @Test
    fun commentsRiskControlThenRetry() =
        scenario(
            "comments-retry",
            { serve(FakeBilibili.COMMENTS, "comments_last.json").serveOnce(FakeBilibili.COMMENTS, "risk_352.json") },
        ) { it.comments(bvid) }

    @Test
    fun commentsHtmlBlockThenGiveUp() = scenario("comments-blocked", { serve(FakeBilibili.COMMENTS, "blocked.html") }) { it.comments(bvid) }

    // endregion

    // region Uploader space

    private val web = BilibiliChannelPageKey(page = 1, lastAid = 0)

    @Test
    fun channelInfo() = scenario("channel-info", { serve(FakeBilibili.CARD, "card.json") }) { it.channelInfo(546195) }

    @Test
    fun channelMissing() = scenario("channel-missing", { serve(FakeBilibili.CARD, "card_missing.json") }) { it.channelInfo(1) }

    @Test
    fun channelVideosWeb() =
        scenario("channel-videos-web", {
            serve(FakeBilibili.VIDEOS_WEB, "videos_web.json")
        }) { it.channelVideos(546195, web) }

    @Test
    fun channelVideosPopular() =
        scenario("channel-videos-popular", { serve(FakeBilibili.VIDEOS_WEB, "videos_web.json") }) {
            it.channelVideos(546195, web.copy(sort = BilibiliChannelSort.POPULAR))
        }

    @Test
    fun channelVideosFallThroughBlockedApis() =
        scenario(
            "channel-videos-fallback",
            {
                serve(FakeBilibili.VIDEOS_WEB, "blocked.html")
                    .serve(FakeBilibili.VIDEOS_SEARCH, "videos_search.json")
                    .serve(FakeBilibili.VIDEOS_APP, "videos_app.json")
            },
        ) { api ->
            // blocked web → search; choice sticks
            listOf(api.channelVideos(546195, web), api.channelVideos(546195, web.copy(page = 2)))
        }

    @Test
    fun channelVideosAppApiByAid() =
        scenario(
            "channel-videos-app",
            {
                serve(FakeBilibili.VIDEOS_WEB, "blocked.html")
                    .serve(FakeBilibili.VIDEOS_SEARCH, "blocked.html")
                    .serve(FakeBilibili.VIDEOS_APP, "videos_app.json")
            },
        ) { it.channelVideos(546195, BilibiliChannelPageKey(2, lastAid = 80433022)) }

    @Test
    fun channelPlaylists() =
        scenario("channel-playlists", {
            serve(FakeBilibili.SEASONS_SERIES, "seasons_series.json")
        }) { it.channelPlaylists(546195, 1) }

    @Test
    fun playlistVideosSeries() =
        scenario("playlist-series", { serve(FakeBilibili.SERIES_ARCHIVES, "playlist_archives.json") }) {
            it.playlistVideos(BilibiliPlaylistKind.SERIES, 546195, 81, 1)
        }

    @Test
    fun playlistVideosSeason() =
        scenario("playlist-season", { serve(FakeBilibili.SEASON_ARCHIVES, "playlist_archives.json") }) {
            it.playlistVideos(BilibiliPlaylistKind.SEASON, 546195, 71, 2)
        }

    // endregion

    // region Live

    private fun FakeBilibili.liveRoom(base: String) = serve(FakeBilibili.LIVE_BASE, base).serve(FakeBilibili.CARD, "card.json")

    @Test
    fun liveRoom() = scenario("live-room", { liveRoom("live_base.json") }) { it.liveRoom(545068) }

    @Test
    fun liveRoomFetchesMissingAvatar() = scenario("live-room-avatar", { liveRoom("live_base_offline.json") }) { it.liveRoom(545068) }

    @Test
    fun livePlayback() =
        scenario(
            "live-playback",
            { liveRoom("live_base.json").serve(FakeBilibili.LIVE_PLAY, "live_play.json") },
        ) { it.livePlayback(545068) }

    @Test
    fun livePlaybackOffline() = scenario("live-offline", { liveRoom("live_base_offline.json") }) { it.livePlayback(545068) }

    @Test
    fun livePlaybackRebroadcast() = scenario("live-rebroadcast", { liveRoom("live_base_rebroadcast.json") }) { it.livePlayback(545068) }

    @Test
    fun livePlaybackWithoutStream() =
        scenario(
            "live-no-stream",
            { liveRoom("live_base.json").serve(FakeBilibili.LIVE_PLAY, "live_play_empty.json") },
        ) { it.livePlayback(545068) }

    @Test
    fun liveRecommended() =
        scenario("live-recommended", {
            serve(FakeBilibili.LIVE_RECOMMENDED, "live_recommended.json")
        }) { it.recommendedLives() }

    @Test
    fun channelLiveRoomOn() =
        scenario("channel-live-on", { serve(FakeBilibili.LIVE_STATUS, "live_status_on.json") }) { it.channelLiveRoom(71) }

    @Test
    fun channelLiveRoomOff() =
        scenario("channel-live-off", {
            serve(FakeBilibili.LIVE_STATUS, "live_status_off.json")
        }) { it.channelLiveRoom(71) }

    @Test
    fun channelLiveRoomFailureIsNull() =
        scenario("channel-live-error", {
            serve(FakeBilibili.LIVE_STATUS, "blocked.html")
        }) { it.channelLiveRoom(71) }

    // endregion
}
