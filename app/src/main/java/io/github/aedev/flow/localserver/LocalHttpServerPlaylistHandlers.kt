package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import java.io.OutputStream
import io.github.aedev.flow.data.model.Video as FlowVideo

private const val MAX_PLAYLIST_NAME = 100

/** `id`: video being saved; marks the playlists that hold it. */
internal fun RequestContext.handleApiPlaylists(
    reply: Reply,
    params: Query,
) {
    val containing = params["id"]?.let { dbHelper.playlistIdsOf(it) }.orEmpty()
    reply.sendJson(PlaylistChoices(playlistChoices(dbHelper.userPlaylists(), containing)))
}

internal fun RequestContext.handleApiMyList(
    reply: Reply,
    params: Query,
) {
    val info = dbHelper.userPlaylist(params["id"].orEmpty()) ?: throw BadRequestException("Unknown playlist")
    reply.sendJson(MyPlaylistPage(info.ref(), dbHelper.userPlaylistItems(info.id).toCards()))
}

internal fun RequestContext.handleApiLiked(reply: Reply) = reply.sendJson(CardListing(dbHelper.likedItems().toCards()))

/** `create` takes `name`; `add`, `remove`, `rename`, `delete` take `playlist`; `add`/`remove` take the video's `url`. */
internal fun RequestContext.handlePlaylistAction(
    reply: Reply,
    params: Query,
) {
    val url = params["url"].orEmpty()

    fun name() =
        params["name"]
            .orEmpty()
            .trim()
            .take(MAX_PLAYLIST_NAME)
            .ifEmpty { throw BadRequestException("Missing 'name' parameter") }

    fun existing() =
        params["playlist"].orEmpty().also {
            if (dbHelper.userPlaylist(it) ==
                null
            ) {
                throw BadRequestException("Unknown playlist")
            }
        }

    fun addVideo(playlistId: String) {
        if (url.isNotEmpty()) dbHelper.addToPlaylist(playlistId, videoOf(url, params))
    }
    val playlistId =
        when (params["action"]) {
            "create" -> dbHelper.createPlaylist(name()).also(::addVideo)
            "add" -> existing().also(::addVideo)
            "remove" -> existing().also { dbHelper.removeFromPlaylist(it, url) }
            "rename" -> existing().also { dbHelper.renamePlaylist(it, name()) }
            "delete" -> existing().also { dbHelper.deletePlaylist(it) }
            else -> throw BadRequestException("Unknown action")
        }
    reply.sendJson(Status("success", playlist = playlistId))
}

private fun videoOf(
    url: String,
    params: Query,
): FlowVideo {
    val videoId = LocalServerMedia.getVideoId(url).ifEmpty { throw BadRequestException("Not a video") }
    return buildFlowVideoFromParams(
        videoId,
        params["title"].takeUnless { it.isNullOrEmpty() } ?: "Shared Item",
        params["uploader"].orEmpty(),
        params["thumbnail"],
        params["uploaderUrl"],
    ).copy(serviceId = serviceIdOfVideo(videoId))
}
