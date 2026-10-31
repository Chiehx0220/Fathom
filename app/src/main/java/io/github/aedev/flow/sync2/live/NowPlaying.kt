package io.github.aedev.flow.sync2.live

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.sync2.data.LinkedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Resend `now` this often while playing, so devices that joined late catch up. */
private const val REPEAT_MS = 30_000L

/** A position further than this from where the last `now` puts it is a seek. */
private const val SEEK_MS = 2_000L

/**
 * What this device plays, told to the group as `now` (spec/live.md, Playing on another device): when
 * the video, state, queue or volume changes, after a seek, and every 30 seconds while playing.
 */
@Singleton
class NowPlaying
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val live: LiveChannel,
        private val db: LinkedDatabase,
    ) {
        private val video = EnhancedPlayerManager.getInstance()
        private val lock = Mutex()
        private var last: LiveMessage.Now? = null
        private var lastAt = 0L

        /** Whether either player is playing, which keeps live running in the background. */
        val playing: Flow<Boolean> =
            combine(GlobalPlayerState.playerState, EnhancedMusicPlayerManager.playerState) { video, music ->
                video.isPlaying || music.isPlaying
            }.distinctUntilChanged()

        /** Sends `now` until cancelled. */
        suspend fun run() =
            coroutineScope {
                launch {
                    merge(
                        GlobalPlayerState.currentVideo.map { it?.id },
                        GlobalPlayerState.playerState.map { it.isPlaying },
                        video.currentQueueIndexState,
                        EnhancedMusicPlayerManager.currentTrack.map { it?.videoId },
                        EnhancedMusicPlayerManager.playerState.map { it.isPlaying to it.position / SEEK_MS },
                        EnhancedMusicPlayerManager.queue.map { it.size },
                    ).collect { publish(force = false) }
                }
                while (true) {
                    delay(REPEAT_MS)
                    if (onMain().state == LiveMessage.PLAYING) publish(force = true)
                }
            }

        private suspend fun publish(force: Boolean) {
            lock.withLock {
                val now = onMain().copy(link = db.groups().group()?.link)
                val previous = last
                if (!force && previous != null && !changed(previous, now)) return
                if (live.send(now) != null) {
                    last = now
                    lastAt = System.currentTimeMillis()
                }
            }
        }

        /** Anything but the position moving on as expected; nothing loaded counts once. */
        private fun changed(
            previous: LiveMessage.Now,
            now: LiveMessage.Now,
        ): Boolean {
            if (previous.copy(pos = now.pos) != now) return true
            val expected = previous.pos + if (previous.state == LiveMessage.PLAYING) System.currentTimeMillis() - lastAt else 0
            return now.video != null && abs(now.pos - expected) > SEEK_MS
        }

        /** The players live on the main thread. */
        private suspend fun onMain(): LiveMessage.Now = withContext(Dispatchers.Main.immediate) { current() }

        /** The player in use: music while it plays or when the video player is idle, otherwise video. */
        private fun current(): LiveMessage.Now {
            val track = EnhancedMusicPlayerManager.currentTrack.value
            val music = EnhancedMusicPlayerManager.playerState.value
            val vol = volume()
            if (track != null && (music.isPlaying || !GlobalPlayerState.playerState.value.isPlaying)) {
                val queue = EnhancedMusicPlayerManager.queue.value
                return LiveMessage.Now(
                    video = track.videoId,
                    title = track.title,
                    by = track.artist,
                    music = true,
                    pos = music.position,
                    dur = music.duration,
                    state = if (music.isPlaying) LiveMessage.PLAYING else LiveMessage.PAUSED,
                    vol = vol,
                    queue = queue.take(LiveMessage.QUEUE_LIMIT).map { it.videoId },
                    index = queue.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0),
                )
            }
            val current = GlobalPlayerState.currentVideo.value ?: return LiveMessage.Now(video = null)
            val queue = video.queueVideos.value
            return LiveMessage.Now(
                video = current.id,
                title = current.title,
                by = current.channelName,
                pos = video.getCurrentPosition(),
                dur = video.getDuration().coerceAtLeast(0),
                state = if (GlobalPlayerState.playerState.value.isPlaying) LiveMessage.PLAYING else LiveMessage.PAUSED,
                vol = vol,
                queue = queue.take(LiveMessage.QUEUE_LIMIT).map { it.id },
                index = video.currentQueueIndexState.value.coerceAtLeast(0),
            )
        }

        /** The media volume, 0 to 100. */
        private fun volume(): Int {
            val audio = context.getSystemService(AudioManager::class.java) ?: return 100
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
        }
    }
