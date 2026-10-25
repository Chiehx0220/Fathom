package io.github.aedev.flow.localserver

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.aedev.flow.bilibili.fake.FakeBilibili
import io.github.aedev.flow.bilibili.fake.verifyGolden
import io.github.aedev.flow.di.bilibiliApiProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Bilibili backend over the fake client. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class)
class LocalServerBilibiliGoldenTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val backend = backendFor(5)
    private val saved = bilibiliApiProvider

    private val fake =
        FakeBilibili()
            .serve(FakeBilibili.VIEW, "view.json")
            .serve(FakeBilibili.PLAYURL, "playurl.json")
            .serve(FakeBilibili.RELATED, "related.json")
            .serve(FakeBilibili.POPULAR, "popular.json")
            .serve(FakeBilibili.SEARCH, "search_video.json")
            .serve(FakeBilibili.CARD, "card.json")
            .serve(FakeBilibili.VIDEOS_WEB, "videos_web.json")
            .serve(FakeBilibili.SEASONS_SERIES, "seasons_series.json")
            .serve(FakeBilibili.SERIES_ARCHIVES, "playlist_archives.json")
            .serve(FakeBilibili.COMMENTS, "comments.json")
            .serve(FakeBilibili.REPLIES, "comment_replies.json")
            .serve(FakeBilibili.DANMAKU, "danmaku.xml")
            .serve(FakeBilibili.LIVE_BASE, "live_base.json")
            .serve(FakeBilibili.LIVE_PLAY, "live_play.json")
            .serve(FakeBilibili.LIVE_RECOMMENDED, "live_recommended.json")
            .serve(FakeBilibili.LIVE_STATUS, "live_status_on.json")

    @Before
    fun serveFake() {
        val api = fake.api()
        bilibiliApiProvider = { api }
    }

    @After
    fun restore() {
        bilibiliApiProvider = saved
    }

    private fun InfoItem.line(): String =
        buildString {
            append("${this@line::class.simpleName} name=$name url=$url thumb=$thumbnailUrl")
            when (val item = this@line) {
                is StreamInfoItem -> {
                    append(" type=${item.streamType} uploader=${item.uploaderName} uploaderUrl=${item.uploaderUrl}")
                    append(
                        " avatar=${item.uploaderAvatarUrl} duration=${item.duration} views=${item.viewCount} date=${item.textualUploadDate}",
                    )
                }

                is PlaylistInfoItem -> {
                    append(" uploader=${item.uploaderName} count=${item.streamCount}")
                }

                is ChannelInfoItem -> {
                    append(" subscribers=${item.subscriberCount}")
                }

                is CommentsInfoItem -> {
                    append(" id=${item.commentId} text=${item.commentText?.content} author=${item.uploaderName} likes=${item.likeCount}")
                    append(
                        " pinned=${item.isPinned} hearted=${item.isHeartedByUploader} replies=${item.replyCount} replyPage=${item.replies?.url}",
                    )
                    append(" date=${item.textualUploadDate}")
                }

                else -> {}
            }
        }

    private fun List<InfoItem>.lines() = joinToString("\n") { it.line() }

    private fun Page?.token() = this?.url

    private fun StreamInfo.render() =
        listOf(
            "name=$name url=$url id=$id type=$streamType duration=$duration views=$viewCount likes=$likeCount",
            "description=${description?.content}",
            "uploader=$uploaderName url=$uploaderUrl avatars=${uploaderAvatars.map { it.url }}",
            "date=${uploadDate?.offsetDateTime()} thumbs=${thumbnails.map { it.url }}",
            "hls=$hlsUrl",
            "videoOnly:\n" + videoOnlyStreams.joinToString("\n") { "${it.id} ${it.resolution} ${it.content} ${it.codec}" },
            "audio:\n" + audioStreams.joinToString("\n") { "${it.id} ${it.content} ${it.codec} ${it.averageBitrate}" },
            "related:\n" + relatedItems.lines(),
        ).joinToString("\n")

    @Test
    fun home() {
        val first = backend.home(context, null)
        val second = backend.home(context, Page("2"))
        verifyGolden(
            "server-home",
            "page1 next=${first.next.token()}\n${first.items.lines()}\npage2 next=${second.next.token()}\n${second.items.lines()}",
        )
    }

    @Test
    fun search() {
        val first = backend.search(context, "cat dog", null)
        val second = backend.search(context, "cat dog", Page("2"))
        verifyGolden(
            "server-search",
            "page1 next=${first.next.token()}\n${first.items.lines()}\npage2 next=${second.next.token()}\n${second.items.lines()}",
        )
    }

    @Test
    fun channelVideos() {
        val page = backend.channel(context, "https://space.bilibili.com/546195", "videos", null, null)
        val header = page.header
        verifyGolden(
            "server-channel",
            "${header.name} ${header.url} ${header.avatarUrl} ${header.bannerUrl} ${header.subscriberCount} ${header.description}\nnext=${page.next.token()}\n${page.items.lines()}",
        )
    }

    @Test
    fun channelPlaylists() {
        val page = backend.channel(context, "546195", "playlists", null, null)
        verifyGolden("server-channel-playlists", "next=${page.next.token()}\n${page.items.lines()}")
    }

    @Test
    fun playlist() {
        val id =
            io.github.aedev.flow.bilibili.BilibiliRef
                .Series(
                    io.github.aedev.flow.bilibili.BilibiliPlaylistKind.SERIES,
                    546195,
                    81,
                    "My series",
                ).id
        val page = backend.playlist(context, id, null)
        val header = page.header
        verifyGolden(
            "server-playlist",
            "${header.name} ${header.url} ${header.uploaderName} ${header.streamCount}\nnext=${page.next.token()}\n${page.items.lines()}",
        )
    }

    @Test
    fun comments() {
        val first = backend.comments(context, "https://www.bilibili.com/video/BV1GJ411x7h7?p=1", null)
        val replies = backend.comments(context, "https://www.bilibili.com/video/BV1GJ411x7h7?p=1", Page("r:9001:1"))
        verifyGolden(
            "server-comments",
            "first next=${first.next.token()} disabled=${first.disabled}\n${first.items.lines()}\nreplies next=${replies.next.token()}\n${replies.items.lines()}",
        )
    }

    @Test
    fun danmaku() {
        val bullets = backend.danmaku(context, "https://www.bilibili.com/video/BV1GJ411x7h7?p=1")
        verifyGolden("server-danmaku", bullets.joinToString("\n") { "${it.text} ${it.color} ${it.position} ${it.time} ${it.size}" })
    }

    @Test
    fun streamInfoVod() {
        verifyGolden("server-stream-vod", backend.streamInfo(context, "https://www.bilibili.com/video/BV1GJ411x7h7?p=2").render())
    }

    @Test
    fun streamInfoLive() {
        verifyGolden("server-stream-live", backend.streamInfo(context, "https://live.bilibili.com/545068").render())
    }
}
