package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import org.json.JSONObject
import java.io.OutputStream
import io.github.aedev.flow.data.model.Video as FlowVideo

private const val MAX_PLAYLIST_NAME = 100

/** `id`: video being saved; marks the playlists that hold it. */
internal fun RequestContext.handleApiPlaylists(
    reply: Reply,
    params: Map<String, String>,
) {
    val containing = params["id"]?.let { dbHelper.playlistIdsOf(it) }.orEmpty()
    reply.sendJson(JSONObject().put("playlists", playlistChoicesJson(dbHelper.userPlaylists(), containing)))
}

internal fun RequestContext.handleApiMyList(
    reply: Reply,
    params: Map<String, String>,
) {
    val info = dbHelper.userPlaylist(params["id"].orEmpty()) ?: throw BadRequestException("Unknown playlist")
    val header = JSONObject().put("id", info.id).put("name", info.name).put("videoCount", info.videoCount)
    reply.sendJson(JSONObject().put("playlist", header).put("videos", mixedVideosJson(dbHelper.userPlaylistItems(info.id))))
}

internal fun RequestContext.handleApiLiked(reply: Reply) =
    reply.sendJson(JSONObject().put("videos", mixedVideosJson(dbHelper.likedItems())))

/** `create` takes `name`; `add`, `remove`, `rename`, `delete` take `playlist`; `add`/`remove` take the video's `url`. */
internal fun RequestContext.handlePlaylistAction(
    reply: Reply,
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
    reply.sendJson(JSONObject().put("status", "success").put("playlist", playlistId))
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
