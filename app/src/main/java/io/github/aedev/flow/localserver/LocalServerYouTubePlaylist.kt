package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page

/**
 * Short YouTube playlist via app repository (follows app fixes), complete first page only; else extractor. InnerTube lacks video count:
 * header -1.
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
