package io.github.aedev.flow.localserver

import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import io.github.aedev.flow.data.model.Video as FlowVideo

internal fun HistoryDbHelper.playlistRepository() = PlaylistRepository(appContext)

/** Playlist bookmarks and Watch Later: native PlaylistRepository (Room); Watch Later = internal playlist, same table as Flow UI saves. */
fun HistoryDbHelper.isPlaylistBookmarked(playlistUrl: String?): Boolean {
    val playlistId = playlistUrlToId(playlistUrl) ?: return false
    return runBlocking { playlistRepository().isExternalPlaylistSaved(playlistId) }
}

fun HistoryDbHelper.bookmarkPlaylist(
    playlistUrl: String,
    name: String?,
    thumbnailUrl: String?,
) {
    val playlistId = playlistUrlToId(playlistUrl) ?: return
    runBlocking { playlistRepository().saveExternalVideoPlaylist(playlistId, name ?: "", "", thumbnailUrl ?: "") }
}

fun HistoryDbHelper.unbookmarkPlaylist(playlistUrl: String) {
    val playlistId = playlistUrlToId(playlistUrl) ?: return
    runBlocking { playlistRepository().unsaveExternalPlaylist(playlistId) }
}

/** Bookmarked playlists as [InfoItem]s. No uploader field (`PlaylistEntity` has none): SPA card subtitle omits it. */
fun HistoryDbHelper.bookmarkedPlaylists(): List<InfoItem> =
    runBlocking {
        playlistRepository().getSavedVideoPlaylistsFlow().first().map { info ->
            val item = PlaylistInfoItem(0, playlistIdToUrl(info.id), info.name)
            if (info.thumbnailUrl.isNotEmpty()) {
                item.thumbnailUrl = info.thumbnailUrl
            }
            item
        }
    }

fun HistoryDbHelper.isWatchLater(videoUrl: String): Boolean {
    val videoId = LocalServerMedia.getVideoId(videoUrl)
    if (videoId.isEmpty()) return false
    return runBlocking { playlistRepository().isInWatchLater(videoId) }
}

/** HTTP action params carry no duration/viewCount/uploadDate (button markup only); `-1`/empty as [StreamInfoItem.toFlowVideo]. */
internal fun buildFlowVideoFromParams(
    videoId: String,
    title: String,
    uploader: String,
    thumbnailUrl: String?,
    uploaderUrl: String?,
) = FlowVideo(
    id = videoId,
    title = title,
    channelName = uploader,
    channelId = channelUrlToId(uploaderUrl) ?: "",
    thumbnailUrl = thumbnailUrl ?: "",
    duration = 0,
    viewCount = -1,
    uploadDate = "",
)

/** Also reports SAVED to FlowNeuroEngine (as native toggleWatchLater), best-effort. */
fun HistoryDbHelper.addWatchLater(
    url: String,
    title: String,
    uploader: String,
    thumbnailUrl: String?,
    uploaderUrl: String?,
) {
    val videoId = LocalServerMedia.getVideoId(url)
    if (videoId.isEmpty()) return
    val video = buildFlowVideoFromParams(videoId, title, uploader, thumbnailUrl, uploaderUrl)
    runBlocking {
        playlistRepository().addToWatchLater(video)
        ensureFlowNeuroInitialized()
        try {
            FlowNeuroEngine.onVideoInteraction(video, InteractionType.SAVED)
        } catch (e: Exception) {
            serverLog("FlowNeuro SAVED signal error: " + e.message)
        }
    }
}

fun HistoryDbHelper.removeWatchLater(url: String) {
    val videoId = LocalServerMedia.getVideoId(url)
    if (videoId.isEmpty()) return
    runBlocking { playlistRepository().removeFromWatchLater(videoId) }
}

/** Watch Later items as [InfoItem]s; video-only (`renderWatchLaterButton()` call sites). */
fun HistoryDbHelper.watchLaterItems(): List<InfoItem> =
    runBlocking {
        playlistRepository().getVideoOnlyWatchLaterFlow().first().map { it.toStreamInfoItem(0) }
    }

private fun HistoryDbHelper.likedVideosRepository() = LikedVideosRepository.getInstance(appContext)

/** Like/dislike via `LikedVideosRepository` (DataStore), as native likeVideo/dislikeVideo. `-1`/empty as [addWatchLater]. */
fun HistoryDbHelper.likeState(videoUrl: String): String? {
    val videoId = LocalServerMedia.getVideoId(videoUrl)
    if (videoId.isEmpty()) return null
    return runBlocking { likedVideosRepository().getLikeState(videoId).first() }
}

fun HistoryDbHelper.likeVideo(
    url: String,
    title: String,
    uploader: String,
    thumbnailUrl: String?,
    uploaderUrl: String?,
    serviceId: Int,
) {
    val videoId = LocalServerMedia.getVideoId(url)
    if (videoId.isEmpty()) return
    runBlocking {
        likedVideosRepository().likeVideo(
            LikedVideoInfo(videoId = videoId, title = title, thumbnail = thumbnailUrl ?: "", channelName = uploader, serviceId = serviceId),
        )
        reportRatingSignal(videoId, title, uploader, thumbnailUrl, uploaderUrl, InteractionType.LIKED)
    }
}

fun HistoryDbHelper.dislikeVideo(
    url: String,
    title: String,
    uploader: String,
    thumbnailUrl: String?,
    uploaderUrl: String?,
) {
    val videoId = LocalServerMedia.getVideoId(url)
    if (videoId.isEmpty()) return
    runBlocking {
        likedVideosRepository().dislikeVideo(videoId)
        reportRatingSignal(videoId, title, uploader, thumbnailUrl, uploaderUrl, InteractionType.DISLIKED)
    }
}

fun HistoryDbHelper.removeLikeState(url: String) {
    val videoId = LocalServerMedia.getVideoId(url)
    if (videoId.isEmpty()) return
    runBlocking { likedVideosRepository().removeLikeState(videoId) }
}

private suspend fun HistoryDbHelper.reportRatingSignal(
    videoId: String,
    title: String,
    uploader: String,
    thumbnailUrl: String?,
    uploaderUrl: String?,
    type: InteractionType,
) {
    ensureFlowNeuroInitialized()
    try {
        val video = buildFlowVideoFromParams(videoId, title, uploader, thumbnailUrl, uploaderUrl)
        FlowNeuroEngine.onVideoInteraction(video, type)
    } catch (e: Exception) {
        serverLog("FlowNeuro $type signal error: " + e.message)
    }
}
