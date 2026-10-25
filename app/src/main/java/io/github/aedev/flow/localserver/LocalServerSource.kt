package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.utils.youtubeVideoIdFromUrl
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

/** Channel page info. Image urls null if none. */
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

/** List sources: Bilibili client, YouTube NewPipe; service-agnostic. */
internal object LocalServerSource {
    /** Feed of a service without a personalised one; Bilibili only. */
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
        if (serviceId == 0) LocalServerYouTubeSearch.search(query, nextPage)?.let { return it }
        val extractor = LocalServerMedia.getDefaultSearchExtractor(NewPipe.getService(serviceId), query)
        val page = LocalServerMedia.fetchInitialOrPage(extractor, nextPage)
        return ListPage(page.items, page.nextPage)
    }

    /** [sort]: YouTube channel videos tab only. */
    fun channel(
        context: Context,
        serviceId: Int,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ): ChannelPage {
        if (LocalServerBilibili.isBilibili(serviceId)) return LocalServerBilibili.channel(context, channelUrl, tab, nextPage)
        if (serviceId == 0) LocalServerYouTubeChannel.videos(channelUrl, tab, sort, nextPage)?.let { return it }

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

    fun playlist(
        context: Context,
        serviceId: Int,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage {
        if (LocalServerBilibili.isBilibili(serviceId)) return LocalServerBilibili.playlist(context, playlistUrl, nextPage)
        if (serviceId == 0) LocalServerYouTubePlaylist.page(context, playlistUrl, nextPage)?.let { return it }
        val extractor = NewPipe.getService(serviceId).getPlaylistExtractor(playlistUrl)
        val page = LocalServerMedia.fetchInitialOrPage(extractor, nextPage)
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

    /** Bilibili only, uploaded videos only (no live). */
    fun danmaku(
        context: Context,
        serviceId: Int,
        mediaUrl: String,
    ): List<Bullet> = if (LocalServerBilibili.isBilibili(serviceId)) LocalServerBilibiliComments.danmaku(context, mediaUrl) else emptyList()
}

/** Stream lists for proxy and DASH manifest, any service. */
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
    // Extractor getters not thread-safe; watch page may read the same one.
    override val length: Long get() = synchronized(extractor) { extractor.length }
    override val videoStreams: List<VideoStream> get() = synchronized(extractor) { extractor.videoStreams.orEmpty() }
    override val videoOnlyStreams: List<VideoStream> get() = synchronized(extractor) { extractor.videoOnlyStreams.orEmpty() }
    override val audioStreams: List<AudioStream> get() = synchronized(extractor) { extractor.audioStreams.orEmpty() }
    override val hlsUrl: String? get() = synchronized(extractor) { extractor.hlsUrl }
}

internal class InfoStreams(
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

/** One video's data for watch page, downloads, subtitles. */
internal fun LocalServerSource.streamInfo(
    context: Context,
    serviceId: Int,
    mediaUrl: String,
): StreamInfo {
    if (LocalServerBilibili.isBilibili(serviceId)) return LocalServerBilibiliStreams.streamInfo(context, mediaUrl)
    val extractor = LocalServerMedia.getCachedExtractor(NewPipe.getService(serviceId), serviceId, mediaUrl)
    val info = synchronized(extractor) { StreamInfo.getInfo(extractor) }
    if (InnerTubeVideoStreamExtractor.supportsService(serviceId)) {
        youtubeVideoIdFromUrl(mediaUrl)?.let { LocalServerYouTubeStreams.overlay(info, it) }
    }
    return info
}

/** [streamInfo] + watch-page related list: Bilibili's own or YouTube's ranked. */
internal fun LocalServerSource.watchInfo(
    dbHelper: HistoryDbHelper,
    serviceId: Int,
    mediaUrl: String,
): StreamInfo {
    val info = streamInfo(dbHelper.appContext, serviceId, mediaUrl)
    if (!LocalServerBilibili.isBilibili(serviceId)) info.relatedItems = dbHelper.relatedVideos(info, serviceId)
    return info
}
