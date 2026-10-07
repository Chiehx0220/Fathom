package io.github.aedev.flow.localserver

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which page, if any, has paired with the server to be remote-controlled. */
data class RemoteLock(
    val locked: Boolean,
    val clientIp: String?,
    val title: String?,
)

/** What the paired page last reported: whether a video is open and how it is playing. */
data class RemoteState(
    val watching: Boolean = false,
    /** A video is open but shrunk to the mini player while the page shows something else. */
    val minimized: Boolean = false,
    val title: String? = null,
    val positionSec: Double = 0.0,
    val durationSec: Double = 0.0,
    val paused: Boolean = true,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fullscreen: Boolean = false,
    /** Chapter titles in order; chapterIndex is the one playing now, -1 if none/chapterless. */
    val chapters: List<String> = emptyList(),
    val chapterIndex: Int = -1,
    /** Active SponsorBlock category (e.g. "sponsor"), null between segments. */
    val skipLabel: String? = null,
    /** The sources the page can switch between (id to name) and which one it is on. */
    val services: List<Pair<Int, String>> = emptyList(),
    val activeService: Int? = null,
) {
    /** Whether there is a video to control, on its own page or in the mini player. */
    val hasVideo: Boolean get() = watching || minimized
}

/** The page paired with the server for remote control, what it last reported, and the commands waiting for it. */
object RemoteSession {
    private val remoteLockState = MutableStateFlow(RemoteLock(false, null, null))
    val remoteLock: StateFlow<RemoteLock> = remoteLockState.asStateFlow()

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

    /** Delivers a command to connected remote pages; set by the running server. */
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
