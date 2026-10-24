package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.serviceIdOfVideo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.model.PlaylistInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import io.github.aedev.flow.data.model.Video as FlowVideo

/** User-created video playlists; Watch Later, saved and music playlists are excluded. */
fun HistoryDbHelper.userPlaylists(): List<PlaylistInfo> =
    runBlocking {
        playlistRepository().getUserCreatedVideoPlaylistsFlow().first()
    }

fun HistoryDbHelper.userPlaylist(playlistId: String): PlaylistInfo? = userPlaylists().firstOrNull { it.id == playlistId }

fun HistoryDbHelper.playlistIdsOf(videoUrl: String): Set<String> {
    val videoId = LocalServerMedia.getVideoId(videoUrl).ifEmpty { return emptySet() }
    return runBlocking { playlistRepository().getPlaylistIdsForVideo(videoId).toSet() }
}

fun HistoryDbHelper.userPlaylistItems(playlistId: String): List<InfoItem> =
    runBlocking {
        playlistRepository().getPlaylistVideosFlow(playlistId).first().map { it.toStreamInfoItem(serviceIdOfVideo(it.id, it.serviceId)) }
    }

/** Id scheme of the app's save-video sheet. */
fun HistoryDbHelper.createPlaylist(name: String): String {
    val id = System.currentTimeMillis().toString()
    runBlocking { playlistRepository().createPlaylist(id, name, "", isPrivate = true) }
    return id
}

fun HistoryDbHelper.renamePlaylist(
    playlistId: String,
    name: String,
) = runBlocking { playlistRepository().updatePlaylistName(playlistId, name) }

fun HistoryDbHelper.deletePlaylist(playlistId: String) = runBlocking { playlistRepository().deletePlaylist(playlistId) }

fun HistoryDbHelper.addToPlaylist(
    playlistId: String,
    video: FlowVideo,
) = runBlocking { playlistRepository().addVideoToPlaylist(playlistId, video) }

fun HistoryDbHelper.removeFromPlaylist(
    playlistId: String,
    videoUrl: String,
) {
    val videoId = LocalServerMedia.getVideoId(videoUrl).ifEmpty { return }
    runBlocking { playlistRepository().removeVideoFromPlaylist(playlistId, videoId) }
}

/** Non-music likes, newest first. */
fun HistoryDbHelper.likedItems(): List<InfoItem> =
    runBlocking {
        LikedVideosRepository.getInstance(appContext).getLikedVideosFlow().first().map { like ->
            FlowVideo(
                id = like.videoId,
                title = like.title,
                channelName = like.channelName,
                channelId = like.channelId.orEmpty(),
                thumbnailUrl = like.thumbnail,
                duration = like.durationSeconds,
                viewCount = -1,
                uploadDate = "",
            ).toStreamInfoItem(serviceIdOfVideo(like.videoId, like.serviceId))
        }
    }

private fun PlaylistInfo.toJson() = JSONObject().put("id", id).put("name", name).put("videoCount", videoCount)

/** Library cards; `isLocal` routes to the in-app playlist screen. */
internal fun myPlaylistsJson(playlists: List<PlaylistInfo>): JSONArray =
    JSONArray(
        playlists.map {
            it
                .toJson()
                .put("serviceId", LocalServerMedia.SERVICE_YOUTUBE)
                .put("thumbnailUrl", it.thumbnailUrl)
                .put("isLocal", true)
        },
    )

/** Picker rows; `contains` marks playlists holding the video. */
internal fun playlistChoicesJson(
    playlists: List<PlaylistInfo>,
    containing: Set<String>,
): JSONArray = JSONArray(playlists.map { it.toJson().put("contains", it.id in containing) })

/** Cards of mixed sources; each takes the service of its own id. */
internal fun mixedVideosJson(items: List<InfoItem>): JSONArray =
    JSONArray(items.map { ApiRenderer.infoItemsToJson(listOf(it), it.serviceId).getJSONObject(0) })
