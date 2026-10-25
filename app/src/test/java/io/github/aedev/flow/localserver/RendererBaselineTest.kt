package io.github.aedev.flow.localserver

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

/** Network-backed route JSON from fixed extractor results. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(application = Application::class)
class RendererBaselineTest {
    private fun images(url: String) = listOf(Image(url, 90, 120, Image.ResolutionLevel.MEDIUM))

    private fun stream(
        id: String,
        type: StreamType = StreamType.VIDEO_STREAM,
        duration: Long = 212,
    ) = StreamInfoItem(0, "https://www.youtube.com/watch?v=$id", "Title $id", type).apply {
        uploaderName = "Uploader"
        uploaderUrl = "https://www.youtube.com/channel/UCuploader"
        uploaderAvatars = images("https://yt3.example/avatar.jpg")
        thumbnails = images("https://i.ytimg.example/$id.jpg")
        this.duration = duration
        viewCount = 1234567
        textualUploadDate = "2 years ago"
    }

    private fun channel() =
        ChannelInfoItem(0, "https://www.youtube.com/channel/UCchan", "Channel").apply {
            thumbnails = images("https://yt3.example/chan.jpg")
            subscriberCount = 4200
        }

    private fun playlist() =
        PlaylistInfoItem(0, "https://www.youtube.com/playlist?list=PLx", "Playlist").apply {
            thumbnails = images("https://i.ytimg.example/pl.jpg")
            streamCount = 12
        }

    private fun comment(
        id: String,
        html: Boolean,
    ) = CommentsInfoItem(0, "https://www.youtube.com/watch?v=jNQXAC9IVRw", "Comment").apply {
        commentId = id
        uploaderName = "Commenter"
        uploaderUrl = "https://www.youtube.com/channel/UCcommenter"
        uploaderAvatars = images("https://yt3.example/c.jpg")
        commentText = Description("text $id", if (html) Description.HTML else Description.PLAIN_TEXT)
        likeCount = 7
        textualUploadDate = "1 day ago"
        isPinned = html
        replyCount = 2
        replies = Page("https://www.youtube.com/replies/$id", id)
    }

    @Test
    fun `search and listing results`() {
        val items =
            listOf(
                stream("aaaaaaaaaaa"),
                stream("bbbbbbbbbbb", StreamType.LIVE_STREAM, 0),
                stream("ccccccccccc", duration = 45),
                channel(),
                playlist(),
            )
        val text =
            buildString {
                append("search\n").append(canonicalJson(ApiJson.encodeToString(items.toSearchResult(Page("https://next/page", "id1")))))
                append("\n\nlast page\n").append(canonicalJson(ApiJson.encodeToString(items.take(1).toSearchResult(null))))
                append("\n\nmixed list\n").append(canonicalJson(ApiJson.encodeToString(items.toCards())))
                append("\n\nerror\n").append(canonicalJson(failureBody(json = true, 400, "boom").first))
                append("\n\nerror without message\n").append(canonicalJson(failureBody(json = true, 400, null).first))
                append("\n")
            }
        verifyBaseline("renderer-listings", text)
    }

    @Test
    fun `channel header and comments`() {
        val header = ChannelHeader("Channel", "https://www.youtube.com/channel/UCchan", "https://yt3.example/chan.jpg", null, 4200, "About")
        val noAvatar = ChannelHeader("Bare", "https://www.youtube.com/channel/UCbare", null, null, -5, "")
        val text =
            buildString {
                append("channel\n").append(canonicalJson(ApiJson.encodeToString(header.toDto(true))))
                append("\n\nchannel without avatar\n").append(canonicalJson(ApiJson.encodeToString(noAvatar.toDto(false))))
                append("\n\ncomments\n")
                append(canonicalJson(ApiJson.encodeToString(listOf(comment("c1", false), comment("c2", true)).map { it.toDto() })))
                append("\n")
            }
        verifyBaseline("renderer-channel-comments", text)
    }

    @Test
    fun `video detail`() {
        val info =
            StreamInfo(
                0,
                "https://www.youtube.com/watch?v=jNQXAC9IVRw",
                "https://www.youtube.com/watch?v=jNQXAC9IVRw",
                StreamType.VIDEO_STREAM,
                "jNQXAC9IVRw",
                "Me at the zoo",
                0,
            ).apply {
                uploaderName = "jawed"
                uploaderUrl = "https://www.youtube.com/channel/UC4QobU6STFB0P71PMvOGN5A"
                uploaderAvatars = images("https://yt3.example/jawed.jpg")
                thumbnails = images("https://i.ytimg.example/zoo.jpg")
                duration = 19
                viewCount = 9_000_000
                likeCount = 12345
                textualUploadDate = "19 years ago"
                description = Description("<b>Hi</b> at 0:05", Description.HTML)
                videoStreams =
                    listOf(
                        VideoStream
                            .Builder()
                            .setId("18")
                            .setContent("https://example.invalid/18", true)
                            .setMediaFormat(MediaFormat.MPEG_4)
                            .setIsVideoOnly(false)
                            .setResolution("360p")
                            .setItagItem(ItagItem(18, ItagItem.ItagType.VIDEO, MediaFormat.MPEG_4, "360p").also { it.height = 360 })
                            .build(),
                    )
                videoOnlyStreams =
                    listOf(
                        VideoStream
                            .Builder()
                            .setId("137")
                            .setContent("https://example.invalid/137", true)
                            .setMediaFormat(MediaFormat.MPEG_4)
                            .setIsVideoOnly(true)
                            .setResolution("1080p")
                            .setItagItem(
                                ItagItem(137, ItagItem.ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, "1080p").also {
                                    it.height = 1080
                                    it.codec =
                                        "avc1.640028"
                                },
                            ).build(),
                    )
                audioStreams =
                    listOf(
                        AudioStream
                            .Builder()
                            .setId("140")
                            .setContent("https://example.invalid/140", true)
                            .setMediaFormat(MediaFormat.M4A)
                            .setAverageBitrate(128)
                            .setItagItem(ItagItem(140, ItagItem.ItagType.AUDIO, MediaFormat.M4A, "128kbps"))
                            .build(),
                    )
                streamSegments =
                    listOf(StreamSegment("Intro", 0), StreamSegment("Zoo", 5).apply { previewUrl = "https://i.ytimg.example/seg.jpg" })
                relatedItems = listOf(stream("rrrrrrrrrrr"), channel())
            }
        verifyBaseline("renderer-video-detail", canonicalJson(ApiJson.encodeToString(info.toDetail(0))) + "\n")
    }
}
