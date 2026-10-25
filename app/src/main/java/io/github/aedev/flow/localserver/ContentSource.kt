package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Remote content for the API routes; [AppContentSource] in app, fakes in tests. */
internal interface ContentSource {
    fun home(
        serviceId: Int,
        nextPage: Page?,
    ): ListPage

    /** YouTube home: no pagination. */
    fun trending(serviceId: Int): List<InfoItem>

    fun search(
        serviceId: Int,
        query: String,
        nextPage: Page?,
    ): ListPage

    fun channel(
        serviceId: Int,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ): ChannelPage

    fun playlist(
        serviceId: Int,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage

    fun comments(
        serviceId: Int,
        videoUrl: String,
        nextPage: Page?,
    ): CommentsResult

    fun streamInfo(
        serviceId: Int,
        mediaUrl: String,
    ): StreamInfo

    /** [streamInfo] + the watch page's related list. */
    fun watchInfo(
        serviceId: Int,
        mediaUrl: String,
    ): StreamInfo

    fun danmaku(
        serviceId: Int,
        mediaUrl: String,
    ): List<Bullet>

    fun sponsorSegments(videoId: String): List<SponsorBlockClient.Segment>
}

internal class AppContentSource(
    private val dbHelper: HistoryDbHelper,
) : ContentSource {
    private val context get() = dbHelper.appContext

    override fun home(
        serviceId: Int,
        nextPage: Page?,
    ) = LocalServerSource.home(context, serviceId, nextPage)

    override fun trending(serviceId: Int) = dbHelper.fetchTrendingItems(serviceId)

    override fun search(
        serviceId: Int,
        query: String,
        nextPage: Page?,
    ) = LocalServerSource.search(context, serviceId, query, nextPage)

    override fun channel(
        serviceId: Int,
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
    ) = LocalServerSource.channel(context, serviceId, channelUrl, tab, sort, nextPage)

    override fun playlist(
        serviceId: Int,
        playlistUrl: String,
        nextPage: Page?,
    ) = LocalServerSource.playlist(context, serviceId, playlistUrl, nextPage)

    override fun comments(
        serviceId: Int,
        videoUrl: String,
        nextPage: Page?,
    ) = LocalServerSource.comments(context, serviceId, videoUrl, nextPage)

    override fun streamInfo(
        serviceId: Int,
        mediaUrl: String,
    ) = LocalServerSource.streamInfo(context, serviceId, mediaUrl)

    override fun watchInfo(
        serviceId: Int,
        mediaUrl: String,
    ) = LocalServerSource.watchInfo(dbHelper, serviceId, mediaUrl)

    override fun danmaku(
        serviceId: Int,
        mediaUrl: String,
    ) = LocalServerSource.danmaku(context, serviceId, mediaUrl)

    override fun sponsorSegments(videoId: String) = SponsorBlockClient.fetchSegments(videoId)
}
