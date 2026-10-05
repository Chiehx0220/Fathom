package io.github.aedev.flow.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

/**
 * A [ForwardingPlayer] whose media session can be told that [getMediaMetadata] now answers differently.
 *
 * Media3's session attaches its own Player.Listener straight to whatever player this wraps (that's
 * ForwardingPlayer's default addListener/removeListener), and only re-asks getMediaMetadata() when that
 * real player fires an event - a transition, a timeline change. GlobalPlayerState.currentVideo finishes
 * enriching well after that event already fired for this video, so overriding the getter alone is
 * invisible to the session: it's stuck on whatever it read at that first ask. Tracking the session's
 * listener here too (still forwarded to the real player as before, so normal playback events are
 * untouched) lets [notifyMediaMetadataChanged] push the corrected value through it directly once
 * GlobalPlayerState actually has it.
 */
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

    /** Pushes the session's metadata again whenever [currentVideo] changes, until the returned job is cancelled. */
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
