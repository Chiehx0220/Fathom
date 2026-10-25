package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.utils.youtubeVideoIdFromUrl
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfo

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
    if (LocalServerBilibili.isBilibili(serviceId)) BilibiliBackend else NewPipeBackend(serviceId)

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

/** NewPipeExtractor service; YouTube (id 0) via InnerTube sources first. */
private class NewPipeBackend(
    private val serviceId: Int,
) : LocalServerBackend {
    private val isYouTube get() = serviceId == 0

    override fun home(
        context: Context,
        nextPage: Page?,
    ): ListPage = throw IllegalArgumentException("Service $serviceId has no plain home feed")

    override fun search(
        context: Context,
        query: String,
        nextPage: Page?,
    ): ListPage {
        if (isYouTube) LocalServerYouTubeSearch.search(query, nextPage)?.let { return it }
        val extractor = LocalServerMedia.getDefaultSearchExtractor(NewPipe.getService(serviceId), query)
        val page = LocalServerMedia.fetchInitialOrPage(extractor, nextPage)
        return ListPage(page.items, page.nextPage)
    }

    override fun channel(
        context: Context,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ): ChannelPage {
        if (isYouTube) LocalServerYouTubeChannel.videos(channelUrl, tab, sort, nextPage)?.let { return it }

        val service = NewPipe.getService(serviceId)
        val channelExtractor = service.getChannelExtractor(channelUrl)
        channelExtractor.fetchPage()

        // Non-default sort: tab link handler built by hand (tab list handlers carry default order).
        val tabHandler: ListLinkHandler =
            if (sort != null) {
                service.channelTabLHFactory.fromQuery(channelExtractor.id, listOf(tab), sort, channelExtractor.baseUrl)
            } else {
                LocalServerMedia.resolveChannelTabHandler(channelExtractor, tab)
            }
        val tabExtractor = service.getChannelTabExtractor(tabHandler)
        val page = LocalServerMedia.fetchInitialOrPage(tabExtractor, nextPage)
        LocalServerMedia.backfillUploaderUrl(page.items, channelUrl)

        val avatar =
            WebFormat.getThumbnailUrl(channelExtractor.avatars).takeIf {
                WebFormat.hasThumbnail(channelExtractor.avatars)
            }
        val banner =
            WebFormat.getThumbnailUrl(channelExtractor.banners).takeIf {
                WebFormat.hasThumbnail(channelExtractor.banners)
            }
        val header =
            ChannelHeader(
                name = channelExtractor.name ?: "",
                url = channelExtractor.linkHandler.url,
                avatarUrl = avatar,
                bannerUrl = banner,
                subscriberCount = channelExtractor.subscriberCount,
                description = channelExtractor.description ?: "",
            )
        return ChannelPage(header, page.items, page.nextPage)
    }

    override fun playlist(
        context: Context,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage {
        if (isYouTube) LocalServerYouTubePlaylist.page(context, playlistUrl, nextPage)?.let { return it }
        val extractor = NewPipe.getService(serviceId).getPlaylistExtractor(playlistUrl)
        val page = LocalServerMedia.fetchInitialOrPage(extractor, nextPage)
        val header = PlaylistHeader(extractor.name, extractor.linkHandler.url, extractor.uploaderName ?: "", extractor.streamCount)
        return PlaylistPage(header, page.items, page.nextPage)
    }

    override fun comments(
        context: Context,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult {
        LocalServerYouTubeComments.comments(context, videoUrl, nextPage)?.let { return it }
        val extractor = NewPipe.getService(serviceId).getCommentsExtractor(videoUrl)
        if (nextPage != null) {
            // getPage() reads only nextPage continuation token, not fetchPage() state: safe on fresh extractor.
            val page = extractor.getPage(nextPage)
            return CommentsResult(page.items, page.nextPage)
        }
        extractor.fetchPage()
        if (extractor.isCommentsDisabled) return CommentsResult(emptyList(), null, disabled = true)
        val page = extractor.initialPage
        return CommentsResult(page.items, page.nextPage)
    }

    override fun streams(
        context: Context,
        mediaUrl: String,
        fresh: Boolean,
    ): StreamLists {
        val service = NewPipe.getService(serviceId)
        val extractorStreams =
            if (!fresh) {
                ExtractorStreams(LocalServerMedia.getCachedExtractor(service, serviceId, mediaUrl))
            } else {
                val extractor = service.getStreamExtractor(mediaUrl)
                extractor.fetchPage()
                ExtractorStreams(extractor)
            }
        if (!InnerTubeVideoStreamExtractor.supportsService(serviceId)) return extractorStreams
        val videoId = youtubeVideoIdFromUrl(mediaUrl) ?: return extractorStreams
        return MergedYouTubeStreams(extractorStreams, videoId)
    }

    override fun streamInfo(
        context: Context,
        mediaUrl: String,
    ): StreamInfo {
        val extractor = LocalServerMedia.getCachedExtractor(NewPipe.getService(serviceId), serviceId, mediaUrl)
        val info = synchronized(extractor) { StreamInfo.getInfo(extractor) }
        if (InnerTubeVideoStreamExtractor.supportsService(serviceId)) {
            youtubeVideoIdFromUrl(mediaUrl)?.let { LocalServerYouTubeStreams.overlay(info, it) }
        }
        return info
    }
}
