package io.github.aedev.flow.player.stream

import android.app.Application
import io.github.aedev.flow.bilibili.BilibiliPlaylistKind
import io.github.aedev.flow.bilibili.BilibiliSearchItem
import io.github.aedev.flow.bilibili.BilibiliSearchType
import io.github.aedev.flow.bilibili.fake.FakeBilibili
import io.github.aedev.flow.bilibili.fake.verifyGolden
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

/** Fake API → mappers → playback step → player streams. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class)
class BilibiliGlueGoldenTest {
    private val fake =
        FakeBilibili()
            .serve(FakeBilibili.VIEW, "view.json")
            .serve(FakeBilibili.PLAYURL, "playurl.json")
            .serve(FakeBilibili.RELATED, "related.json")
            .serve(FakeBilibili.POPULAR, "popular.json")
            .serve(FakeBilibili.SEARCH, "search_video.json")
            .serve(FakeBilibili.VIDEOS_WEB, "videos_web.json")
            .serve(FakeBilibili.LIVE_BASE, "live_base.json")
            .serve(FakeBilibili.LIVE_PLAY, "live_play.json")
            .serve(FakeBilibili.LIVE_RECOMMENDED, "live_recommended.json")
            .serve(FakeBilibili.CARD, "card.json")

    private val api = fake.api()
    private val request =
        PlaybackResolutionRequest(
            "BV1GJ411x7h7?p=2",
            isWifi = true,
            escalateToSabr = false,
            resumePositionOverrideMs = 1500,
            allowShorts = false,
        )
    private val preferences = StreamPreferences(VideoQuality.AUTO, "", "", "")

    /** Drops wall-clock stamp. */
    private fun Video.stable() = copy(timestamp = 0)

    private fun VideoStream.line() =
        "$id | $resolution | $content | codec=$codec | ${bitrate}b | ${width}x$height | ${fps}fps | videoOnly=$isVideoOnly"

    private fun AudioStream.line() = "$id | $content | codec=$codec | ${averageBitrate}b"

    @Test
    fun mappers() {
        val out =
            runBlocking {
                val info = api.videoInfo("BV1GJ411x7h7", 2)
                val search = api.search("cat", BilibiliSearchType.VIDEO, 1).items.filterIsInstance<BilibiliSearchItem.Video>()
                val channel =
                    api.channelVideos(
                        546195,
                        io.github.aedev.flow.bilibili
                            .BilibiliChannelPageKey(1, 0),
                    )
                listOf(
                    api.related("BV1GJ411x7h7").map { BilibiliVideoMapper.videoFromRelated(it).stable() },
                    api.popular(1).map { BilibiliVideoMapper.videoFromRelated(it).stable() },
                    search.map { BilibiliVideoMapper.videoFromSearch(it).stable() },
                    channel.videos.map { BilibiliVideoMapper.videoFromChannel(it, 546195, "Fallback name", "https://a/f.jpg").stable() },
                    BilibiliVideoMapper.videoFromInfo("BV1GJ411x7h7?p=2", info).stable(),
                    BilibiliVideoMapper
                        .videoFromInfo(
                            "BV1GJ411x7h7?p=2",
                            info.copy(title = "", thumbnailUrl = ""),
                            Video(
                                id = "x",
                                title = "Fallback title",
                                channelName = "Fb",
                                channelId = "7",
                                thumbnailUrl = "https://fb/t.jpg",
                                duration = 0,
                                viewCount = 0,
                                uploadDate = "d",
                                channelThumbnailUrl = "https://fb/a.jpg",
                            ),
                        ).stable(),
                    BilibiliVideoMapper.videoFromLive("live:545068", api.liveRoom(545068)).stable(),
                    api.recommendedLives().map { BilibiliVideoMapper.videoFromLiveItem(it).stable() },
                )
            }
        verifyGolden("glue-mappers", out)
    }

    @Test
    fun vodResolution() {
        val out =
            runBlocking {
                val step = BilibiliPlaybackSource(api).resolve(request, preferences)
                val streams = step.buildStreams()
                listOf(
                    "info=${step.playback.info.bvid}/${step.playback.info.cid}",
                    "related=${step.relatedVideos.map { it.id }}",
                    "resume=${step.resumePositionOverrideMs}",
                    "video:\n" + streams.videoStreams.joinToString("\n") { it.line() },
                    "audio:\n" + streams.audioStreams.joinToString("\n") { it.line() },
                    "selectedVideo=${streams.selectedVideo?.id}",
                    "selectedAudio=${streams.selectedAudio?.id}",
                )
            }
        verifyGolden("glue-vod", out.joinToString("\n"))
    }

    @Test
    fun liveResolution() {
        val out =
            runBlocking {
                val step = BilibiliPlaybackSource(api).resolveLive(request.copy(videoId = "live:545068"))
                step.playback
            }
        verifyGolden("glue-live", out)
    }

    @Test
    fun playlistAndLiveNotLiveId() {
        val failure = runCatching { runBlocking { BilibiliPlaybackSource(api).resolveLive(request) } }.exceptionOrNull()
        verifyGolden("glue-live-bad-id", failure)
        check(BilibiliPlaylistKind.entries.size == 2)
    }
}
