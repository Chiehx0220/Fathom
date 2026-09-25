package io.github.aedev.flow.ui.components.layout.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import org.schabi.newpipe.extractor.ServiceList

/**
 * The app-wide way to open the page behind a piece of media: its channel, artist, album or playlist.
 *
 * Cards and quick action sheets call this directly instead of taking a callback from every screen
 * that shows them, so one implementation decides how the players get out of the way and how a page
 * that is already open is not opened again.
 */
@Stable
interface MediaNavigator {
    /** [serviceId] says which service the channel belongs to; a bare Bilibili uploader id is recognized without it. */
    fun openChannel(
        channelId: String,
        serviceId: Int = ServiceList.YouTube.serviceId,
    )

    fun openArtist(artistId: String)

    fun openAlbum(albumId: String)

    fun openMusicPlaylist(playlistId: String)
}

private object NoOpMediaNavigator : MediaNavigator {
    override fun openChannel(
        channelId: String,
        serviceId: Int,
    ) = Unit

    override fun openArtist(artistId: String) = Unit

    override fun openAlbum(albumId: String) = Unit

    override fun openMusicPlaylist(playlistId: String) = Unit
}

/** Static because the shell provides one navigator for its whole lifetime. Previews and tests get a no-op. */
val LocalMediaNavigator = staticCompositionLocalOf<MediaNavigator> { NoOpMediaNavigator }
