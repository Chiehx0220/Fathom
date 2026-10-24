package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import org.json.JSONObject
import java.io.OutputStream
import io.github.aedev.flow.data.model.Video as FlowVideo

private const val MAX_PLAYLIST_NAME = 100

private fun ClientHandler.sendJson(
    os: OutputStream,
    json: JSONObject,
) = sendResponse(os, 200, json.toString(), "application/json")

/** `id`: video being saved; marks the playlists that hold it. */
internal fun ClientHandler.handleApiPlaylists(
    os: OutputStream,
    params: Map<String, String>,
) {
    val containing = params["id"]?.let { dbHelper.nativePlaylistIdsOf(it) }.orEmpty()
    sendJson(os, JSONObject().put("playlists", playlistChoicesJson(dbHelper.nativeUserPlaylists(), containing)))
}

internal fun ClientHandler.handleApiMyList(
    os: OutputStream,
    params: Map<String, String>,
) {
    val info = dbHelper.nativeUserPlaylist(params["id"].orEmpty()) ?: throw BadRequestException("Unknown playlist")
    val header = JSONObject().put("id", info.id).put("name", info.name).put("videoCount", info.videoCount)
    sendJson(os, JSONObject().put("playlist", header).put("videos", mixedVideosJson(dbHelper.nativeUserPlaylistItems(info.id))))
}

internal fun ClientHandler.handleApiLiked(os: OutputStream) =
    sendJson(os, JSONObject().put("videos", mixedVideosJson(dbHelper.nativeLikedItems())))

/** `create` takes `name`; `add`, `remove`, `rename`, `delete` take `playlist`; `add`/`remove` take the video's `url`. */
internal fun ClientHandler.handlePlaylistAction(
    os: OutputStream,
    params: Map<String, String>,
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
            if (dbHelper.nativeUserPlaylist(it) ==
                null
            ) {
                throw BadRequestException("Unknown playlist")
            }
        }

    fun addVideo(playlistId: String) {
        if (url.isNotEmpty()) dbHelper.nativeAddToPlaylist(playlistId, videoOf(url, params))
    }
    val playlistId =
        when (params["action"]) {
            "create" -> dbHelper.nativeCreatePlaylist(name()).also(::addVideo)
            "add" -> existing().also(::addVideo)
            "remove" -> existing().also { dbHelper.nativeRemoveFromPlaylist(it, url) }
            "rename" -> existing().also { dbHelper.nativeRenamePlaylist(it, name()) }
            "delete" -> existing().also { dbHelper.nativeDeletePlaylist(it) }
            else -> throw BadRequestException("Unknown action")
        }
    sendJson(os, JSONObject().put("status", "success").put("playlist", playlistId))
}

private fun videoOf(
    url: String,
    params: Map<String, String>,
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
