package io.github.aedev.flow.ui.screens.player

import android.content.Context
import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliLiveId
import io.github.aedev.flow.bilibili.BilibiliVideoInfo
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.di.bilibiliApi
import io.github.aedev.flow.innertube.models.response.VideoChapter
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.error.VideoErrorMapper
import io.github.aedev.flow.player.stream.LiveFromBilibili
import io.github.aedev.flow.player.stream.VodFromBilibili
import io.github.aedev.flow.ui.screens.player.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Bilibili steps of [PlaybackSessionApplier]; outside the upstream file (clean merges). */
internal class BilibiliPlaybackApplier(
    private val context: Context,
    private val uiState: MutableStateFlow<VideoPlayerUiState>,
    private val isLoadCurrent: (Long) -> Boolean,
    private val playbackPreparer: PlaybackPreparer,
    private val streamPreparer: PlaybackStreamPreparer,
    private val secondaryMetadata: PlayerSecondaryMetadataLoader,
    private val viewHistory: ViewHistory,
    private val playerManager: EnhancedPlayerManager,
    private val scope: CoroutineScope,
    private val networkDispatcher: CoroutineDispatcher,
    private val recordWatchClick: (Video) -> Unit,
    private val liveChat: LiveChatController,
) {
    suspend fun applyVod(
        load: LoadContext,
        step: VodFromBilibili,
    ) {
        try {
            prepareVodStreamFromBilibili(load, step)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bilibili VOD failed for ${load.videoId}", e)
            val videoError = VideoErrorMapper.from(context, e, load.videoId)
            if (isLoadCurrent(load.token)) {
                uiState.update { it.applyVodFailure(step.relatedVideos, videoError) }
            }
        }
    }

    suspend fun applyLive(
        load: LoadContext,
        step: LiveFromBilibili,
    ) {
        try {
            prepareLiveStreamFromBilibili(load, step)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bilibili live failed for ${load.videoId}", e)
            val videoError = VideoErrorMapper.from(context, e, load.videoId)
            if (isLoadCurrent(load.token)) {
                uiState.update { it.applyVodFailure(emptyList(), videoError) }
            }
        }
    }

    /**
     * Bilibili live room: HLS master via the YouTube live-stream player path. No related lane; chat socket shared by the danmaku layer and [liveChat].
     */
    private suspend fun prepareLiveStreamFromBilibili(
        load: LoadContext,
        step: LiveFromBilibili,
    ) = withContext(Dispatchers.Main) {
        if (!isLoadCurrent(load.token)) return@withContext

        val videoId = load.videoId
        val streams = streamPreparer.assembleLive(videoId, uiState.value.cachedVideo, step)
        val identity = streams.identity
        GlobalPlayerState.setCurrentVideo(identity.enrichedVideo)
        recordWatchClick(identity.enrichedVideo)
        playbackPreparer.beginSession(videoId, identity.title, identity.channel, identity.thumbnail)

        uiState.update { it.applyLiveStreams(emptyList(), streams.hlsUrl).copy(cachedVideo = identity.enrichedVideo) }

        val liveStarted =
            playbackPreparer.prepareLiveStreams(
                videoId = videoId,
                hlsUrl = streams.hlsUrl,
                dashManifestUrl = null,
                subtitles = emptyList(),
                isCurrent = { isLoadCurrent(load.token) },
                progressiveStream = streams.progressiveStream,
            )
        if (!liveStarted) return@withContext

        secondaryMetadata.loadChannelMetadata(
            videoId = videoId,
            uploaderUrl = null,
            channelId = identity.channelId,
            embeddedAvatarUrls = identity.embeddedAvatarUrls,
            loadToken = load.token,
        )
        val roomId = step.playback.room.roomId
        playerManager.startLiveDanmaku(roomId)
        liveChat.start(BilibiliLiveId.of(roomId))
    }

    /**
     * Bilibili counterpart of [prepareVodStreamFromInnerTube]. No watch-response metadata (heatmap, chapters, category, watch info): only fillable lanes armed.
     */
    private suspend fun prepareVodStreamFromBilibili(
        load: LoadContext,
        step: VodFromBilibili,
    ) = withContext(Dispatchers.Main) {
        if (!isLoadCurrent(load.token)) return@withContext

        val videoId = load.videoId
        val playback = step.playback
        val streams = streamPreparer.assembleVod(videoId, uiState.value.cachedVideo, step)
        val identity = streams.identity

        GlobalPlayerState.setCurrentVideo(identity.enrichedVideo)
        recordWatchClick(identity.enrichedVideo)
        playbackPreparer.beginSession(videoId, identity.title, identity.channel, identity.thumbnail)
        val autoplay = playbackPreparer.applyAutoplayCandidates(videoId = videoId, videos = step.relatedVideos)

        val savedPositionMs =
            step.resumePositionOverrideMs
                ?.takeIf { it > 0L }
                ?: viewHistory.getPlaybackPosition(videoId).first()

        Log.w(
            TAG,
            "VOD playing $videoId via native Bilibili client (video=${streams.videoStreams.size}, audio=${streams.audioStreams.size})",
        )

        uiState.update {
            it.applyVodStreams(
                cachedVideo = identity.enrichedVideo,
                isArchivedLivestream = false,
                relatedVideos = step.relatedVideos,
                videoStream = streams.videoStream,
                audioStream = streams.audioStream,
                availableQualities = streams.availableQualities,
                savedPositionMs = savedPositionMs,
                isAdaptiveMode = streams.isAdaptiveMode,
                autoplayEnabled = autoplay,
                innerTubeVideoFormats = emptyList(),
                innerTubeAudioFormats = emptyList(),
                streamSizes = streams.streamSizes,
                storyboard = emptyList(),
            )
        }

        loadChapters(load, playback.info)
        secondaryMetadata.loadRelatedVideos(videoId, step.relatedVideos, load.token)
        secondaryMetadata.loadChannelMetadata(
            videoId = videoId,
            uploaderUrl = null,
            channelId = identity.channelId,
            embeddedAvatarUrls = identity.embeddedAvatarUrls,
            loadToken = load.token,
        )

        playbackPreparer.prepareBilibiliVodStreams(
            videoId = videoId,
            streams = streams,
            step = step,
            savedPositionMs = savedPositionMs,
            isCurrent = { isLoadCurrent(load.token) },
        )

        playerManager.loadDanmaku(bilibiliApi(context), playback.info)
    }

    /** Off playback path: slow/failing chapter request never delays first frame. */
    private fun loadChapters(
        load: LoadContext,
        info: BilibiliVideoInfo,
    ) {
        scope.launch(networkDispatcher) {
            val chapters =
                try {
                    withTimeoutOrNull(8_000L) { bilibiliApi(context).chapters(info) }.orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Bilibili chapters failed for ${load.videoId}: ${e.message}")
                    emptyList()
                }
            Log.w(TAG, "Bilibili chapters for ${load.videoId}: ${chapters.size}")
            if (chapters.isEmpty() || !isLoadCurrent(load.token)) return@launch
            uiState.update { state ->
                state.applyChapters(chapters.map { VideoChapter(it.title, it.startSeconds, it.imageUrl) })
            }
        }
    }

    private companion object {
        const val TAG = "BilibiliPlaybackApplier"
    }
}
