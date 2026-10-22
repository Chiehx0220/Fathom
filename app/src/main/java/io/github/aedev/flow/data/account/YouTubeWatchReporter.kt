package io.github.aedev.flow.data.account

import android.util.Log
import io.github.aedev.flow.data.local.PrivacyGate
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.utils.cipher.PipePipeNsigDecoder
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** View reporting to the account; no-op unless sync is on. */
interface WatchReporting {
    fun begin(videoId: String)

    fun progress(
        videoId: String,
        positionMs: Long,
    )

    fun finish(
        videoId: String,
        positionMs: Long,
    )

    object None : WatchReporting {
        override fun begin(videoId: String) = Unit

        override fun progress(
            videoId: String,
            positionMs: Long,
        ) = Unit

        override fun finish(
            videoId: String,
            positionMs: Long,
        ) = Unit
    }
}

private const val TAG = "YouTubeWatchReporter"
private const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
private const val CPN_LENGTH = 16
private const val WATCHTIME_STEP_MS = 30_000L
private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")

internal fun newClientPlaybackNonce(random: Random = Random.Default): String =
    buildString(CPN_LENGTH) { repeat(CPN_LENGTH) { append(CPN_ALPHABET[random.nextInt(CPN_ALPHABET.length)]) } }

/** [params] override same-named query values of [base]. */
internal fun trackingUrl(
    base: String,
    params: Map<String, String>,
): String =
    URLBuilder(base)
        .apply { params.forEach { (key, value) -> parameters[key] = value } }
        .buildString()

internal fun playbackParams(cpn: String): Map<String, String> = mapOf("ver" to "2", "cpn" to cpn)

internal fun watchtimeParams(
    cpn: String,
    startMs: Long,
    positionMs: Long,
    final: Boolean,
): Map<String, String> {
    val end = (positionMs / 1000.0).toString()
    return buildMap {
        putAll(playbackParams(cpn))
        put("st", (startMs / 1000.0).toString())
        put("et", end)
        put("cmt", end)
        put("state", "playing")
        if (final) put("final", "1")
    }
}

/** Playback and watchtime pings from a signed-in TVHTML5 `/player` response; unsigned tracking URLs do not count. */
@Singleton
class YouTubeWatchReporter
    @Inject
    constructor(
        private val store: GoogleAccountStore,
        private val privacy: PrivacyGate,
    ) : WatchReporting {
        private class Pings(
            val watchtimeUrl: String?,
            val cpn: String,
        ) {
            var reportedMs = 0L
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val sessions = ConcurrentHashMap<String, Pings>()
        private val started = ConcurrentHashMap.newKeySet<String>()
        private val lock = Mutex()

        override fun begin(videoId: String) {
            if (!YOUTUBE_ID.matches(videoId) || !started.add(videoId)) return
            scope.launch {
                if (!allowed()) {
                    started.remove(videoId)
                    return@launch
                }
                val signature = PipePipeNsigDecoder.signatureTimestamp() ?: return@launch log("$videoId: no signature timestamp")
                val tracking = YouTube.playbackTracking(videoId, signature).getOrNull() ?: return@launch log("$videoId: no tracking")
                val playbackUrl = tracking.videostatsPlaybackUrl?.baseUrl ?: return@launch log("$videoId: no playback url")
                val cpn = newClientPlaybackNonce()
                sessions[videoId] = Pings(tracking.videostatsWatchtimeUrl?.baseUrl, cpn)
                send(videoId, "playback", trackingUrl(playbackUrl, playbackParams(cpn)))
            }
        }

        override fun progress(
            videoId: String,
            positionMs: Long,
        ) {
            val pings = sessions[videoId] ?: return
            if (positionMs - pings.reportedMs < WATCHTIME_STEP_MS) return
            watchtime(videoId, pings, positionMs, final = false)
        }

        override fun finish(
            videoId: String,
            positionMs: Long,
        ) {
            val pings = sessions.remove(videoId)
            started.remove(videoId)
            if (pings == null || positionMs <= pings.reportedMs) return
            watchtime(videoId, pings, positionMs, final = true)
        }

        private fun watchtime(
            videoId: String,
            pings: Pings,
            positionMs: Long,
            final: Boolean,
        ) {
            val url = pings.watchtimeUrl ?: return
            val from = pings.reportedMs
            pings.reportedMs = positionMs
            scope.launch {
                if (!allowed()) return@launch
                send(videoId, "watchtime", trackingUrl(url, watchtimeParams(pings.cpn, from, positionMs, final)))
            }
        }

        private suspend fun allowed(): Boolean =
            YouTube.cookie != null && store.syncEnabled.first() && !privacy.isDeepFlowActive() && !privacy.isWatchHistoryPaused()

        private suspend fun send(
            videoId: String,
            kind: String,
            url: String,
        ) = lock.withLock {
            val ok = YouTube.sendTracking(url).getOrNull()
            log("$videoId $kind ping ${if (ok == true) "accepted" else "failed"}")
        }

        private fun log(message: String) {
            Log.d(TAG, message)
        }
    }
