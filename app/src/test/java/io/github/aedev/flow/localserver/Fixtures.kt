package io.github.aedev.flow.localserver

import io.github.aedev.flow.content.ChannelInfoItem
import io.github.aedev.flow.content.CommentsInfoItem
import io.github.aedev.flow.content.Description
import io.github.aedev.flow.content.Image
import io.github.aedev.flow.content.Page
import io.github.aedev.flow.content.PlaylistInfoItem
import io.github.aedev.flow.content.StreamInfo
import io.github.aedev.flow.content.StreamInfoItem
import io.github.aedev.flow.content.StreamSegment
import io.github.aedev.flow.player.stream.model.AudioStream
import io.github.aedev.flow.player.stream.model.ItagInfo
import io.github.aedev.flow.player.stream.model.MediaFormat
import io.github.aedev.flow.player.stream.model.StreamType
import io.github.aedev.flow.player.stream.model.VideoStream

internal fun images(url: String) = listOf(Image(url, 90, 120, Image.ResolutionLevel.MEDIUM))

internal fun stream(
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

internal fun channel() =
    ChannelInfoItem(0, "https://www.youtube.com/channel/UCchan", "Channel").apply {
        thumbnails = images("https://yt3.example/chan.jpg")
        subscriberCount = 4200
    }

internal fun playlist() =
    PlaylistInfoItem(0, "https://www.youtube.com/playlist?list=PLx", "Playlist").apply {
        thumbnails = images("https://i.ytimg.example/pl.jpg")
        streamCount = 12
    }

internal fun comment(
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

internal fun zooInfo(): StreamInfo =
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
                VideoStream(
                    "18",
                    "https://example.invalid/18",
                    MediaFormat.MPEG_4,
                    ItagInfo(18, ItagInfo.ItagType.VIDEO, MediaFormat.MPEG_4, "360p", 0).also { it.height = 360 },
                    "360p",
                    isVideoOnly = false,
                ),
            )
        videoOnlyStreams =
            listOf(
                VideoStream(
                    "137",
                    "https://example.invalid/137",
                    MediaFormat.MPEG_4,
                    ItagInfo(137, ItagInfo.ItagType.VIDEO_ONLY, MediaFormat.MPEG_4, "1080p", 0).also {
                        it.height = 1080
                        it.codec = "avc1.640028"
                    },
                    "1080p",
                    isVideoOnly = true,
                ),
            )
        audioStreams =
            listOf(
                AudioStream(
                    "140",
                    "https://example.invalid/140",
                    MediaFormat.M4A,
                    ItagInfo(140, ItagInfo.ItagType.AUDIO, MediaFormat.M4A, "128kbps", 0),
                    averageBitrate = 128,
                ),
            )
        streamSegments =
            listOf(StreamSegment("Intro", 0), StreamSegment("Zoo", 5).apply { previewUrl = "https://i.ytimg.example/seg.jpg" })
        relatedItems = listOf(stream("rrrrrrrrrrr"), channel())
    }
