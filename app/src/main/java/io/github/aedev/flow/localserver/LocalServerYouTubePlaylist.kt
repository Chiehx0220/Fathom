package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page

/**
 * A short YouTube playlist through the app's own playlist repository, the one its playlist screen reads, so the
 * page lists what the app lists and follows the app's fixes instead of the extractor's.
 *
 * Only a complete first page is answered here. A list with a continuation, a first page that is full, or one
 * without a title (some playlist kinds have another header) is left to the extractor, which pages them and
 * knows their size: InnerTube's response here does not say how many videos a playlist has, so the header's
 * count is unknown (-1) and the web page leaves it out.
 */
internal object LocalServerYouTubePlaylist {
    private const val YOUTUBE = 0
    private const val UNKNOWN_COUNT = -1L
    private const val FULL_PAGE = 100

    fun page(
        context: Context,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage? {
        if (nextPage != null) return null
        val id = playlistUrlToId(playlistUrl) ?: return null
        val repository = localServerEntryPoint(context).youTubePlaylistRepository()
        return try {
            runBlocking {
                val first = repository.firstPage(id) ?: return@runBlocking null
                if (first.title.isBlank() || first.continuation != null || first.videos.size >= FULL_PAGE) return@runBlocking null
                val header = PlaylistHeader(first.title, playlistUrl, first.ownerName.orEmpty(), UNKNOWN_COUNT)
                PlaylistPage(header, items(first.videos), null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            serverLog("InnerTube playlist failed: ${e.message}")
            null
        }
    }

    private fun items(videos: List<Video>): List<InfoItem> = videos.map { it.toStreamInfoItem(YOUTUBE) }
}
