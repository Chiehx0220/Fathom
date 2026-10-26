package io.github.aedev.flow.localserver

import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.channel.ChannelTabContent
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.ui.youtubeChannelBrowseId
import io.github.aedev.flow.ui.youtubeChannelUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page

/**
 * YouTube Videos tab via app InnerTube (follows app fixes). Continuation = [Page] url "yt:u:<token>" with owner in id/ids (no header);
 * unprefixed pages and other tabs: extractor.
 */
internal object LocalServerYouTubeChannel {
    private const val MORE = "yt:u:"
    private const val YOUTUBE = 0

    /** Continuation made by this object; [LocalServerSource] does not hand it to the extractor. */
    fun owns(page: Page?): Boolean = page?.url?.startsWith(MORE) == true

    /** Videos tab page; null → extractor answers (other tab/sort, or InnerTube empty). */
    fun videos(
        channelUrl: String,
        tab: String,
        sort: String?,
        nextPage: Page?,
        withAbout: Boolean = true,
    ): ChannelPage? {
        if (tab != "videos" || sort != null) return null
        if (nextPage != null && !owns(nextPage)) return null
        return try {
            runBlocking { load(channelUrl, nextPage, withAbout) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            serverLog("InnerTube channel failed: ${e.message}")
            // Failed continuation ends the list; failed first page → extractor.
            nextPage?.takeIf { owns(it) }?.let { ChannelPage(header(ownerOf(it), channelUrl), emptyList(), null) }
        }
    }

    /** Uploads of [channelUrl], newest first; null if InnerTube gave none. About request omitted. */
    fun uploads(channelUrl: String): List<InfoItem>? =
        videos(channelUrl, "videos", null, null, withAbout = false)?.items?.takeIf { it.isNotEmpty() }

    private suspend fun load(
        channelUrl: String,
        nextPage: Page?,
        withAbout: Boolean,
    ): ChannelPage? {
        if (nextPage != null) {
            val owner = ownerOf(nextPage)
            val content = YouTube.channelTabContinuation(nextPage.url.removePrefix(MORE), owner, ChannelTabKind.Videos).getOrThrow()
            return ChannelPage(
                header(owner, channelUrl, subscribersOf(nextPage)),
                itemsOf(content, channelUrl),
                nextOf(content, owner, subscribersOf(nextPage)),
            )
        }

        val browseId =
            youtubeChannelBrowseId(channelUrl)
                ?: YouTube.resolveChannelId(youtubeChannelUrl(channelUrl) ?: return null).getOrThrow()
        val landing = (if (withAbout) YouTube.channel(browseId) else YouTube.channelLanding(browseId)).getOrThrow()
        val owner =
            FeedItemOwner(
                id = landing.header.id.ifBlank { browseId },
                name = landing.header.title,
                avatarUrl = landing.header.avatarUrl,
            )
        val content =
            landing.initialTab?.takeIf { it.kind == ChannelTabKind.Videos }
                ?: landing.tabs.firstOrNull { it.kind == ChannelTabKind.Videos }?.let { tab ->
                    val params = tab.params ?: ChannelTabKind.Videos.defaultParams ?: return null
                    YouTube.channelTab(browseId, params, owner, ChannelTabKind.Videos).getOrThrow()
                }
                ?: return null

        val header =
            ChannelHeader(
                name = landing.header.title,
                url = channelUrlOf(owner.id, channelUrl),
                avatarUrl = landing.header.avatarUrl.takeIf { it.isNotBlank() },
                bannerUrl = landing.header.bannerUrl?.takeIf { it.isNotBlank() },
                subscriberCount = landing.header.subscriberCount ?: -1,
                description = landing.header.description.orEmpty(),
            )
        return ChannelPage(header, itemsOf(content, channelUrl), nextOf(content, owner, header.subscriberCount))
    }

    private fun itemsOf(
        content: ChannelTabContent,
        channelUrl: String,
    ): List<InfoItem> {
        val items =
            content.items
                .filterIsInstance<FeedItem.VideoItem>()
                .map { it.video.toStreamInfoItem(YOUTUBE) as InfoItem }
        LocalServerMedia.backfillUploaderUrl(items, channelUrl)
        return items
    }

    private fun nextOf(
        content: ChannelTabContent,
        owner: FeedItemOwner,
        subscribers: Long,
    ): Page? =
        content.continuation?.let {
            Page(MORE + it, owner.id, listOf(owner.name, owner.avatarUrl, subscribers.toString()), null, null)
        }

    private fun subscribersOf(page: Page): Long = page.ids?.getOrNull(2)?.toLongOrNull() ?: -1

    private fun ownerOf(page: Page): FeedItemOwner =
        FeedItemOwner(
            id = page.id.orEmpty(),
            name = page.ids?.getOrNull(0).orEmpty(),
            avatarUrl = page.ids?.getOrNull(1).orEmpty(),
        )

    /** Continuation has no header: handed owner + count stand in. */
    private fun header(
        owner: FeedItemOwner,
        channelUrl: String,
        subscribers: Long = -1,
    ) = ChannelHeader(
        name = owner.name,
        url = channelUrlOf(owner.id, channelUrl),
        avatarUrl = owner.avatarUrl.takeIf { it.isNotBlank() },
        bannerUrl = null,
        subscriberCount = subscribers,
        description = "",
    )

    private fun channelUrlOf(
        id: String,
        fallback: String,
    ) = if (id.startsWith("UC")) "https://www.youtube.com/channel/$id" else fallback
}
