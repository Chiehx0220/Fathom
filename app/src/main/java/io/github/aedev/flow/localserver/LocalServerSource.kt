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

/** Routes by service id to its [LocalServerBackend]. */
internal object LocalServerSource {
    /** Plain home feed (services without a personalised one). */
    fun home(
        context: Context,
        serviceId: Int,
        nextPage: Page?,
    ): ListPage = backendFor(serviceId).home(context, nextPage)

    fun search(
        context: Context,
        serviceId: Int,
        query: String,
        nextPage: Page?,
    ): ListPage = backendFor(serviceId).search(context, query, nextPage)

    /** [sort]: YouTube channel videos tab only. */
    fun channel(
        context: Context,
        serviceId: Int,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ): ChannelPage = backendFor(serviceId).channel(context, channelUrl, tab, sort, nextPage)

    fun playlist(
        context: Context,
        serviceId: Int,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage = backendFor(serviceId).playlist(context, playlistUrl, nextPage)

    fun comments(
        context: Context,
        serviceId: Int,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult = backendFor(serviceId).comments(context, videoUrl, nextPage)

    /** Uploaded videos only; none where unsupported. */
    fun danmaku(
        context: Context,
        serviceId: Int,
        mediaUrl: String,
    ): List<Bullet> = backendFor(serviceId).danmaku(context, mediaUrl)
}

/** Stream lists for proxy and DASH manifest, any service. */
internal interface StreamLists {
    val length: Long
    val videoStreams: List<VideoStream>
    val videoOnlyStreams: List<VideoStream>
    val audioStreams: List<AudioStream>
    val hlsUrl: String?
}

internal class ExtractorStreams(
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
): StreamLists = backendFor(serviceId).streams(context, mediaUrl, fresh)

/** One video's data for watch page, downloads, subtitles. */
internal fun LocalServerSource.streamInfo(
    context: Context,
    serviceId: Int,
    mediaUrl: String,
): StreamInfo = backendFor(serviceId).streamInfo(context, mediaUrl)

/** [streamInfo] + related list (service's own or ranked). */
internal fun LocalServerSource.watchInfo(
    dbHelper: HistoryDbHelper,
    serviceId: Int,
    mediaUrl: String,
): StreamInfo {
    val backend = backendFor(serviceId)
    val info = backend.streamInfo(dbHelper.appContext, mediaUrl)
    if (!backend.ownsRelated) info.relatedItems = dbHelper.relatedVideos(info, serviceId)
    return info
}
