package io.github.aedev.flow.localserver

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Web page → app hand-off request. */
data class Handoff(
    val url: String,
    val title: String,
    val videoId: String,
    val positionMs: Long?,
)

/** Page paired for remote control, if any. */
data class RemoteLock(
    val locked: Boolean,
    val clientIp: String?,
    val title: String?,
)

/** Switchable picture quality: player id + label. */
data class RemoteQuality(
    val id: String,
    val label: String,
)

/** Local like state; recommendation ranking only. */
enum class RemoteRating { NONE, LIKED, DISLIKED }

/** Paired page's last report: video open, playback state. */
data class RemoteState(
    val watching: Boolean = false,
    /** Video open, shrunk to mini player. */
    val minimized: Boolean = false,
    val title: String? = null,
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val paused: Boolean = true,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fullscreen: Boolean = false,
    /** Chapter titles in order; chapterIndex = playing, -1 none. */
    val chapters: List<String> = emptyList(),
    val chapterIndex: Int = -1,
    /** Active SponsorBlock category (e.g. "sponsor"); null between segments. */
    val skipLabel: String? = null,
    /** Switchable sources (id → name) + current. */
    val services: List<Pair<Int, String>> = emptyList(),
    val activeService: Int? = null,
    /** Picture qualities, tallest first; empty if no choice. */
    val qualities: List<RemoteQuality> = emptyList(),
    /** Playing quality label + chosen id ("auto" = player decides). */
    val quality: String? = null,
    val chosenQuality: String = "auto",
    val rate: Float = 1f,
    /** Danmaku overlay on/off; null if source has none. */
    val danmaku: Boolean? = null,
    /** Null: not reported. */
    val rating: RemoteRating? = null,
) {
    /** Video to control: own page or mini player. */
    val hasVideo: Boolean get() = watching || minimized
}

/** Paired page, last report, pending commands. */
object RemoteSession {
    private val remoteLockState = MutableStateFlow(RemoteLock(false, null, null))
    val remoteLock: StateFlow<RemoteLock> = remoteLockState.asStateFlow()

    internal val choiceHold = RemoteChoiceHold()

    private val handoffFlow = MutableSharedFlow<Handoff>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val handoffs: SharedFlow<Handoff> = handoffFlow.asSharedFlow()

    fun handOff(handoff: Handoff) = handoffFlow.tryEmit(handoff)

    private val remoteStateFlow = MutableStateFlow(RemoteState())
    val remoteState: StateFlow<RemoteState> = remoteStateFlow.asStateFlow()

    fun updateRemoteState(state: RemoteState) {
        remoteStateFlow.value = state
    }

    @Volatile
    private var activeLockCode: String? = null

    @Volatile
    private var activeClientIp: String? = null

    @Volatile
    private var activeVideoTitle: String? = null

    /** Delivers commands to connected remote pages; set by running server. */
    @Volatile
    var commandBroadcaster: ((String) -> Unit)? = null

    private val pendingCommands = java.util.concurrent.LinkedBlockingQueue<String>()

    fun getAndClearPendingCommands(): List<String> {
        val copy = ArrayList<String>()
        var cmd: String?
        while (pendingCommands.poll().also { cmd = it } != null) {
            copy.add(cmd!!)
        }
        return copy
    }

    fun addPendingCommand(cmd: String?) {
        if (pendingCommands.size > 500) {
            pendingCommands.poll() // Prevent infinite growth if client disconnects
        }
        pendingCommands.offer(cmd!!)

        commandBroadcaster?.invoke(cmd)
    }

    fun getActiveClientIp(): String? = activeClientIp

    fun getActiveVideoTitle(): String? = activeVideoTitle

    fun getActiveLockCode(): String? = activeLockCode

    fun releaseLock() {
        activeLockCode = null
        activeClientIp = null
        activeVideoTitle = null
        remoteLockState.value = RemoteLock(false, null, null)
        remoteStateFlow.value = RemoteState()
    }

    fun tryLock(
        code: String,
        clientIp: String,
        title: String?,
    ): Boolean {
        val currentLockCode = activeLockCode
        if (currentLockCode == null) {
            activeLockCode = code
            activeClientIp = clientIp
            activeVideoTitle = title
            remoteLockState.value = RemoteLock(true, clientIp, title)
            return true
        } else if (currentLockCode == code) {
            activeVideoTitle = title
            remoteLockState.value = RemoteLock(true, activeClientIp, title)
            return true
        }
        return false
    }
}
