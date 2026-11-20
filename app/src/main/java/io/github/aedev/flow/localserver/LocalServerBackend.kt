package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.content.Page
import io.github.aedev.flow.content.StreamInfo
import io.github.aedev.flow.utils.youtubeVideoIdFromUrl

/** Per-service lists, comments, streams; picked by [LocalServerSource]. */
internal interface LocalServerBackend {
    /** Watch-page related list from the service itself (else history-ranked). */
    val ownsRelated: Boolean get() = false

    fun home(
        context: Context,
        nextPage: Page?,
    ): ListPage

    fun search(
        context: Context,
        query: String,
        nextPage: Page?,
    ): ListPage

    fun channel(
        context: Context,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ): ChannelPage

    fun playlist(
        context: Context,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage

    fun comments(
        context: Context,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult

    fun danmaku(
        context: Context,
        mediaUrl: String,
    ): List<Bullet> = emptyList()

    fun streams(
        context: Context,
        mediaUrl: String,
        fresh: Boolean,
    ): StreamLists

    fun streamInfo(
        context: Context,
        mediaUrl: String,
    ): StreamInfo
}

internal fun backendFor(serviceId: Int): LocalServerBackend =
    if (LocalServerBilibili.isBilibili(serviceId)) BilibiliBackend else YouTubeBackend

/** Native Bilibili client. */
private object BilibiliBackend : LocalServerBackend {
    override val ownsRelated = true

    override fun home(
        context: Context,
        nextPage: Page?,
    ) = LocalServerBilibili.home(context, nextPage)

    override fun search(
        context: Context,
        query: String,
        nextPage: Page?,
    ) = LocalServerBilibili.search(context, query, nextPage)

    override fun channel(
        context: Context,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ) = LocalServerBilibili.channel(context, channelUrl, tab, nextPage)

    override fun playlist(
        context: Context,
        playlistUrl: String,
        nextPage: Page?,
    ) = LocalServerBilibili.playlist(context, playlistUrl, nextPage)

    override fun comments(
        context: Context,
        videoUrl: String,
        nextPage: Page?,
    ) = LocalServerBilibiliComments.comments(context, videoUrl, nextPage)

    override fun danmaku(
        context: Context,
        mediaUrl: String,
    ) = LocalServerBilibiliComments.danmaku(context, mediaUrl)

    override fun streams(
        context: Context,
        mediaUrl: String,
        fresh: Boolean,
    ): StreamLists = InfoStreams(LocalServerBilibiliStreams.streamInfo(context, mediaUrl))

    override fun streamInfo(
        context: Context,
        mediaUrl: String,
    ) = LocalServerBilibiliStreams.streamInfo(context, mediaUrl)
}

/** YouTube through the app's InnerTube stack. Unsupported requests throw instead of answering with a made-up empty page. */
private object YouTubeBackend : LocalServerBackend {
    override fun home(
        context: Context,
        nextPage: Page?,
    ): ListPage = throw IllegalArgumentException("YouTube has no plain home feed here")

    override fun search(
        context: Context,
        query: String,
        nextPage: Page?,
    ): ListPage = LocalServerYouTubeSearch.search(query, nextPage) ?: ListPage(emptyList(), null)

    override fun channel(
        context: Context,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ): ChannelPage {
        require(tab == "videos" && sort == null) { "YouTube channel tab '$tab' (sort: $sort) is not supported" }
        return LocalServerYouTubeChannel.videos(channelUrl, tab, sort, nextPage)
            ?: throw IllegalStateException("YouTube channel $channelUrl unavailable")
    }

    override fun playlist(
        context: Context,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage = LocalServerYouTubePlaylist.page(context, playlistUrl, nextPage)

    override fun comments(
        context: Context,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult = LocalServerYouTubeComments.comments(context, videoUrl, nextPage)

    override fun streams(
        context: Context,
        mediaUrl: String,
        fresh: Boolean,
    ): StreamLists {
        val videoId = youtubeVideoIdFromUrl(mediaUrl) ?: LocalServerMedia.getVideoId(mediaUrl)
        if (fresh) LocalServerYouTubeStreams.invalidate(videoId)
        return YouTubeStreamLists(videoId)
    }

    override fun streamInfo(
        context: Context,
        mediaUrl: String,
    ): StreamInfo = LocalServerYouTubeInfo.streamInfo(context, mediaUrl)
}
