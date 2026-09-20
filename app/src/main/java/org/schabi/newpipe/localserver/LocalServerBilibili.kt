package org.schabi.newpipe.localserver

import android.content.Context
import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliChannelId
import io.github.aedev.flow.bilibili.BilibiliChannelPageKey
import io.github.aedev.flow.bilibili.BilibiliPlaylistId
import io.github.aedev.flow.bilibili.BilibiliSearchItem
import io.github.aedev.flow.bilibili.BilibiliSearchType
import io.github.aedev.flow.bilibili.BilibiliVideoId
import io.github.aedev.flow.di.bilibiliApi
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem

/**
 * The local server's Bilibili side, on the native client: ids and links, and the lists (home,
 * search, uploader, playlist). The server's pages are built from NewPipe's item and page types, so
 * results are handed back in those shapes. Comments, danmaku and one video's streams are in
 * [LocalServerBilibiliComments] and [LocalServerBilibiliStreams], the rows in LocalServerBilibiliItems.kt.
 *
 * Every function blocks: the server serves each request on its own thread.
 *
 * Continuation tokens travel as a [Page] whose url holds the token, so they survive the server's
 * page serialisation unchanged: a page number ("2") or "<page>:<lastAid>" for an uploader's videos.
 */
internal object LocalServerBilibili {
    private const val POPULAR_PAGES = 5

    val serviceId: Int get() = BILIBILI_SERVICE_ID

    fun isBilibili(serviceId: Int): Boolean = serviceId == this.serviceId

    fun videoUrl(id: String): String = BilibiliVideoId.toUrl(id)

    fun channelUrl(mid: String): String = "https://space.bilibili.com/$mid"

    /** "https://www.bilibili.com/video/BV1xx?p=2" -> "BV1xx?p=2". */
    fun videoIdOf(url: String): String = BilibiliVideoId.fromUrl(url) ?: url

    // region Lists

    fun home(
        context: Context,
        nextPage: Page?,
    ): ListPage {
        val page = nextPage?.url?.toIntOrNull() ?: 1
        val videos = runBlocking { bilibiliApi(context).popular(page) }
        val items =
            videos.map {
                bilibiliVideoItem("${it.bvid}?p=1", it.title, it.thumbnailUrl, it.durationSec, it.viewCount, it.uploader.name, it.uploader.mid, it.uploader.avatarUrl)
            }
        return ListPage(items, Page((page + 1).toString()).takeIf { videos.isNotEmpty() && page < POPULAR_PAGES })
    }

    fun search(
        context: Context,
        query: String,
        nextPage: Page?,
    ): ListPage {
        val page = nextPage?.url?.toIntOrNull() ?: 1
        val result = runBlocking { bilibiliApi(context).search(query, BilibiliSearchType.VIDEO, page) }
        val items =
            result.items.filterIsInstance<BilibiliSearchItem.Video>().map {
                bilibiliVideoItem("${it.bvid}?p=1", it.title, it.thumbnailUrl, it.durationSec, it.viewCount, it.uploader.name, it.uploader.mid, it.uploader.avatarUrl)
            }
        return ListPage(items, Page((page + 1).toString()).takeIf { result.hasMore })
    }

    /**
     * The uploader's own videos, newest first, for the subscription feed. The name and avatar come
     * from the subscription, so this is one request instead of two.
     */
    fun uploads(
        context: Context,
        channelLink: String,
        channelName: String,
        channelAvatarUrl: String,
    ): List<InfoItem> {
        val mid = BilibiliChannelId.midOf(channelLink) ?: throw IllegalArgumentException("Not a Bilibili channel: $channelLink")
        val page = runBlocking { bilibiliApi(context).channelVideos(mid, BilibiliChannelPageKey(1, 0L)) }
        return page.videos.map { it.toStreamItem(channelName, channelAvatarUrl, channelUrl(mid.toString())) }
    }

    fun channel(
        context: Context,
        channelLink: String,
        tab: String,
        nextPage: Page?,
    ): ChannelPage {
        val mid = BilibiliChannelId.midOf(channelLink) ?: throw IllegalArgumentException("Not a Bilibili channel: $channelLink")
        val api = bilibiliApi(context)
        val info = runBlocking { api.channelInfo(mid) }
        val url = channelUrl(mid.toString())
        val header = ChannelHeader(info.name, url, info.avatarUrl, info.bannerUrl, info.followerCount, info.description)

        if (tab == "playlists") {
            val page = nextPage?.url?.toIntOrNull() ?: 1
            val lists = runBlocking { api.channelPlaylists(mid, page) }
            val items =
                lists.items.map { ref ->
                    PlaylistInfoItem(serviceId, BilibiliPlaylistId.encode(ref.kind, ref.mid, ref.id, ref.name), ref.name).apply {
                        thumbnailUrl = ref.coverUrl
                        setUploaderName(info.name)
                        setStreamCount(ref.videoCount.toLong())
                    }
                }
            return ChannelPage(header, items, Page((page + 1).toString()).takeIf { lists.hasMore })
        }

        val key = nextPage?.url?.split(':')?.let { BilibiliChannelPageKey(it[0].toIntOrNull() ?: 1, it.getOrNull(1)?.toLongOrNull() ?: 0L) }
            ?: BilibiliChannelPageKey(1, 0L)
        val videos = runBlocking { api.channelVideos(mid, key) }
        val items = videos.videos.map { it.toStreamItem(info.name, info.avatarUrl, url) }
        val next = Page("${key.page + 1}:${videos.lastAid}").takeIf { videos.hasMore }
        return ChannelPage(header, items, next)
    }

    fun playlist(
        context: Context,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage {
        val parsed = BilibiliPlaylistId.parse(playlistUrl) ?: throw IllegalArgumentException("Not a Bilibili playlist: $playlistUrl")
        val api = bilibiliApi(context)
        val page = nextPage?.url?.toIntOrNull() ?: 1
        val result = runBlocking { api.playlistVideos(parsed.kind, parsed.mid, parsed.id, page) }
        val owner = runCatching { runBlocking { api.channelInfo(parsed.mid) } }.getOrNull()
        val ownerUrl = channelUrl(parsed.mid.toString())
        val items = result.videos.map { it.toStreamItem(owner?.name.orEmpty(), owner?.avatarUrl.orEmpty(), ownerUrl) }
        val header = PlaylistHeader(parsed.name, playlistUrl, owner?.name.orEmpty(), result.total.toLong())
        return PlaylistPage(header, items, Page((page + 1).toString()).takeIf { page * PLAYLIST_PAGE_SIZE < result.total })
    }

    // endregion

    private const val PLAYLIST_PAGE_SIZE = 30
}
