package io.github.aedev.flow.localserver

import android.content.Context
import org.json.JSONArray
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

internal class ListPage(
    val items: List<InfoItem>,
    val next: Page?,
)

/** What a channel page shows about the channel. Image urls are null when there is none. */
class ChannelHeader(
    val name: String,
    val url: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val subscriberCount: Long,
    val description: String,
)

internal class ChannelPage(
    val header: ChannelHeader,
    val items: List<InfoItem>,
    val next: Page?,
)

class PlaylistHeader(
    val name: String,
    val url: String,
    val uploaderName: String,
    val streamCount: Long,
)

internal class PlaylistPage(
    val header: PlaylistHeader,
    val items: List<InfoItem>,
    val next: Page?,
)

internal class CommentsResult(
    val items: List<CommentsInfoItem>,
    val next: Page?,
    val disabled: Boolean = false,
)

/**
 * Where the local server's lists come from: Bilibili from the native client, YouTube from
 * NewPipe's extractors. A handler asks for a page here and never needs to know which one answered.
 */
internal object LocalServerSource {
    /** The feed of a service that has no personalised one. Only Bilibili is such a service. */
    fun home(
        context: Context,
        serviceId: Int,
        nextPage: Page?,
    ): ListPage {
        require(LocalServerBilibili.isBilibili(serviceId)) { "Service $serviceId has no plain home feed" }
        return LocalServerBilibili.home(context, nextPage)
    }

    fun search(
        context: Context,
        serviceId: Int,
        query: String,
        nextPage: Page?,
    ): ListPage {
        if (LocalServerBilibili.isBilibili(serviceId)) return LocalServerBilibili.search(context, query, nextPage)
        val extractor = LocalHttpServer.getDefaultSearchExtractor(NewPipe.getService(serviceId), query)
        val page = LocalHttpServer.fetchInitialOrPage(extractor, nextPage)
        return ListPage(page.items, page.nextPage)
    }

    /** [sort] only applies to a YouTube channel's videos tab. */
    fun channel(
        context: Context,
        serviceId: Int,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ): ChannelPage {
        if (LocalServerBilibili.isBilibili(serviceId)) return LocalServerBilibili.channel(context, channelUrl, tab, nextPage)

        val service = NewPipe.getService(serviceId)
        val channelExtractor = service.getChannelExtractor(channelUrl)
        channelExtractor.fetchPage()

        // A non-default sort needs the tab's link handler built by hand; the tab list's own
        // handlers carry the default order.
        val tabHandler: ListLinkHandler =
            if (sort != null) {
                service.channelTabLHFactory.fromQuery(channelExtractor.id, listOf(tab), sort, channelExtractor.baseUrl)
            } else {
                LocalHttpServer.resolveChannelTabHandler(channelExtractor, tab)
            }
        val tabExtractor = service.getChannelTabExtractor(tabHandler)
        val page = LocalHttpServer.fetchInitialOrPage(tabExtractor, nextPage)
        LocalHttpServer.backfillUploaderUrl(page.items, channelUrl)

        val avatar = HtmlRendererCommon.getThumbnailUrl(channelExtractor.avatars).takeIf { HtmlRendererCommon.hasThumbnail(channelExtractor.avatars) }
        val banner = HtmlRendererCommon.getThumbnailUrl(channelExtractor.banners).takeIf { HtmlRendererCommon.hasThumbnail(channelExtractor.banners) }
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

    fun playlist(
        context: Context,
        serviceId: Int,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage {
        if (LocalServerBilibili.isBilibili(serviceId)) return LocalServerBilibili.playlist(context, playlistUrl, nextPage)
        val extractor = NewPipe.getService(serviceId).getPlaylistExtractor(playlistUrl)
        val page = LocalHttpServer.fetchInitialOrPage(extractor, nextPage)
        val header = PlaylistHeader(extractor.name, extractor.linkHandler.url, extractor.uploaderName ?: "", extractor.streamCount)
        return PlaylistPage(header, page.items, page.nextPage)
    }

    fun comments(
        context: Context,
        serviceId: Int,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult {
        if (LocalServerBilibili.isBilibili(serviceId)) return LocalServerBilibiliComments.comments(context, videoUrl, nextPage)
        val extractor = NewPipe.getService(serviceId).getCommentsExtractor(videoUrl)
        if (nextPage != null) {
            // getPage() only reads nextPage's continuation token, not fetchPage() state, so it is
            // safe on a fresh extractor.
            val page = extractor.getPage(nextPage)
            return CommentsResult(page.items, page.nextPage)
        }
        extractor.fetchPage()
        if (extractor.isCommentsDisabled) return CommentsResult(emptyList(), null, disabled = true)
        val page = extractor.initialPage
        return CommentsResult(page.items, page.nextPage)
    }

    /** Only Bilibili has danmaku, and only for uploaded videos, not live streams. */
    fun danmaku(
        context: Context,
        serviceId: Int,
        mediaUrl: String,
    ): JSONArray = if (LocalServerBilibili.isBilibili(serviceId)) LocalServerBilibiliComments.danmaku(context, mediaUrl) else JSONArray()
}

/** The stream lists the proxy and the DASH manifest choose from, whichever service they came from. */
internal interface StreamLists {
    val length: Long
    val videoStreams: List<VideoStream>
    val videoOnlyStreams: List<VideoStream>
    val audioStreams: List<AudioStream>
    val hlsUrl: String?
}

private class ExtractorStreams(
    private val extractor: StreamExtractor,
) : StreamLists {
    // The extractor's getters are not thread-safe, and a watch page may be reading the same one.
    override val length: Long get() = synchronized(extractor) { extractor.length }
    override val videoStreams: List<VideoStream> get() = synchronized(extractor) { extractor.videoStreams.orEmpty() }
    override val videoOnlyStreams: List<VideoStream> get() = synchronized(extractor) { extractor.videoOnlyStreams.orEmpty() }
    override val audioStreams: List<AudioStream> get() = synchronized(extractor) { extractor.audioStreams.orEmpty() }
    override val hlsUrl: String? get() = synchronized(extractor) { extractor.hlsUrl }
}

private class InfoStreams(
    private val info: StreamInfo,
) : StreamLists {
    override val length: Long get() = info.duration
    override val videoStreams: List<VideoStream> get() = info.videoStreams.orEmpty()
    override val videoOnlyStreams: List<VideoStream> get() = info.videoOnlyStreams.orEmpty()
    override val audioStreams: List<AudioStream> get() = info.audioStreams.orEmpty()
    override val hlsUrl: String? get() = info.hlsUrl
}

internal fun LocalServerSource.streams(
    context: Context,
    serviceId: Int,
    mediaUrl: String,
    fresh: Boolean,
): StreamLists {
    if (LocalServerBilibili.isBilibili(serviceId)) return InfoStreams(LocalServerBilibiliStreams.streamInfo(context, mediaUrl))
    val service = NewPipe.getService(serviceId)
    if (!fresh) return ExtractorStreams(LocalHttpServer.getCachedExtractor(service, serviceId, mediaUrl))
    val extractor = service.getStreamExtractor(mediaUrl)
    extractor.fetchPage()
    return ExtractorStreams(extractor)
}

/** Everything about one video that the watch page, downloads and subtitles read. */
internal fun LocalServerSource.streamInfo(
    context: Context,
    serviceId: Int,
    mediaUrl: String,
): StreamInfo {
    if (LocalServerBilibili.isBilibili(serviceId)) return LocalServerBilibiliStreams.streamInfo(context, mediaUrl)
    val extractor = LocalHttpServer.getCachedExtractor(NewPipe.getService(serviceId), serviceId, mediaUrl)
    return synchronized(extractor) { StreamInfo.getInfo(extractor) }
}

/** [streamInfo] with the related list a watch page shows: Bilibili's own, or YouTube's ranked one. */
internal fun LocalServerSource.watchInfo(
    dbHelper: HistoryDbHelper,
    serviceId: Int,
    mediaUrl: String,
): StreamInfo {
    val info = streamInfo(dbHelper.appContext, serviceId, mediaUrl)
    if (!LocalServerBilibili.isBilibili(serviceId)) info.relatedItems = dbHelper.nativeRelatedVideos(info, serviceId)
    return info
}
