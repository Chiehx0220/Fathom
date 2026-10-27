package io.github.aedev.flow.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

/** [ForwardingPlayer] so the media session re-reads [getMediaMetadata] after GlobalPlayerState enriches the video. */
internal open class MetadataSyncingPlayer(
    wrapped: Player,
    private val log: (String) -> Unit,
) : ForwardingPlayer(wrapped) {
    private val extraListeners = CopyOnWriteArraySet<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        extraListeners.add(listener)
        log("VideoSessionPlayer addListener count=${extraListeners.size}")
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        extraListeners.remove(listener)
        log("VideoSessionPlayer removeListener count=${extraListeners.size}")
        super.removeListener(listener)
    }

    fun notifyMediaMetadataChanged() {
        val metadata = mediaMetadata
        log(
            "VideoSessionPlayer notifyMediaMetadataChanged title=${metadata.title} " +
                "artist=${metadata.artist} listeners=${extraListeners.size}",
        )
        extraListeners.forEach {
            try {
                it.onMediaMetadataChanged(metadata)
            } catch (e: Exception) {
                log("VideoSessionPlayer notify listener threw ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /** Re-pushes session metadata on every [currentVideo] change, until returned job cancelled. */
    fun syncMetadataWith(
        scope: CoroutineScope,
        currentVideo: Flow<Video?>,
    ): Job =
        scope.launch {
            currentVideo.collect { video ->
                log("VideoSessionPlayer GlobalPlayerState.currentVideo changed id=${video?.id} title=${video?.title}")
                notifyMediaMetadataChanged()
            }
        }
}
