package io.github.aedev.flow.ui.screens.playlists

import android.content.Context
import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliPlaylistId
import io.github.aedev.flow.data.repository.RemotePlaylistPage
import io.github.aedev.flow.di.bilibiliApi
import kotlinx.coroutines.CancellationException

/** Bilibili series/season in YouTube-playlist page shape; loaded whole, no continuation. */
internal suspend fun bilibiliPlaylistPage(
    context: Context,
    playlistId: String,
): RemotePlaylistPage? {
    val ref = BilibiliPlaylistId.parse(playlistId) ?: return null
    return try {
        val details = BilibiliPlaylistLoader.load(bilibiliApi(context), playlistId, ref)
        RemotePlaylistPage(
            title = details.name,
            ownerName = null,
            ownerId = ref.mid.toString(),
            description = "",
            thumbnailUrl = details.thumbnailUrl,
            videos = details.videos,
            continuation = null,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("PlaylistDetail", "Bilibili playlist failed: ${e.message}")
        null
    }
}
