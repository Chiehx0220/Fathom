package io.github.aedev.flow.localserver

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
