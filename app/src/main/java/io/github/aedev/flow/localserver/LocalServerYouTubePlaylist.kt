package io.github.aedev.flow.localserver

import android.content.Context
import io.github.aedev.flow.content.InfoItem
import io.github.aedev.flow.content.Page
import io.github.aedev.flow.content.ServiceIds
import io.github.aedev.flow.data.model.Video

/**
 * YouTube playlist via app playlist repository; continuation `yt:l:<token>`, id in [Page.id].
 * Failed first page throws (route errors); failed continuation ends list.
 */
internal object LocalServerYouTubePlaylist {
    private const val MORE = "yt:l:"
    private const val UNKNOWN_COUNT = -1L

    fun page(
        context: Context,
        playlistUrl: String,
        nextPage: Page?,
    ): PlaylistPage {
        val id =
            nextPage?.id?.takeIf { nextPage.url?.startsWith(MORE) == true }
                ?: playlistUrlToId(playlistUrl)
                ?: throw IllegalArgumentException("Not a YouTube playlist: $playlistUrl")
        val repository = localServerEntryPoint(context).youTubePlaylistRepository()
        return blockingOrElse(
            "InnerTube playlist",
            { e -> if (nextPage != null) PlaylistPage(PlaylistHeader("", playlistUrl, "", UNKNOWN_COUNT), emptyList(), null) else throw e },
        ) {
            if (nextPage != null) {
                val token = nextPage.url?.takeIf { it.startsWith(MORE) }?.removePrefix(MORE)
                val more = token?.let { repository.nextPage(id, it) }
                val header = PlaylistHeader("", playlistUrl, "", UNKNOWN_COUNT)
                PlaylistPage(header, items(more?.first.orEmpty()), more?.second?.let { cursor(id, it) })
            } else {
                val first = repository.firstPage(id) ?: throw IllegalStateException("Playlist $id unavailable")
                val header =
                    PlaylistHeader(
                        first.title,
                        playlistUrl,
                        first.ownerName.orEmpty(),
                        first.videoCount?.toLong() ?: UNKNOWN_COUNT,
                    )
                PlaylistPage(header, items(first.videos), first.continuation?.let { cursor(id, it) })
            }
        }
    }

    private fun cursor(
        playlistId: String,
        token: String,
    ) = Page(url = MORE + token, id = playlistId)

    private fun items(videos: List<Video>): List<InfoItem> = videos.map { it.toStreamInfoItem(ServiceIds.YOUTUBE) }
}
