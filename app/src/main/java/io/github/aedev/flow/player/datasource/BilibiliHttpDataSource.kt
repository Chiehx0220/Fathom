package io.github.aedev.flow.player.datasource

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.error.StreamDenialClassifier

/**
 * One transfer from Bilibili's CDN, opened by [BilibiliRoutingDataSource] in place of
 * [YouTubeHttpDataSource], which stays as upstream has it.
 *
 * Bilibili lists two mirrors per file; when this request has one to fall back to, both may be tried
 * (see [HedgedOpen]) so a stalled mirror costs one short delay rather than seconds. The speed each
 * mirror showed is reported to [BilibiliMirrors] when the transfer ends, to pick the order next time.
 */
@UnstableApi
internal class BilibiliHttpDataSource(
    private val defaultRequestProperties: Map<String, String>,
) : BaseDataSource(true),
    HttpDataSource {
    private var dataSource: DataSource? = null
    private var currentUri: Uri? = null

    private class OpenedSource(
        val source: DataSource,
        val length: Long,
    )

    // The transfer in progress: which URL, and what its reads have moved so far (see BilibiliMirrors.recordSpeed).
    private var speedUrl: String? = null
    private var readBytes = 0L
    private var readNanos = 0L

    @UnstableApi
    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        speedUrl = null
        readBytes = 0L
        readNanos = 0L

        val factory =
            OkHttpDataSource
                .Factory(YouTubeHttpDataSource.sharedClient())
                .setUserAgent(BilibiliHttpSupport.USER_AGENT)

        val requestHeaders = LinkedHashMap<String, String>()
        requestHeaders.putAll(defaultRequestProperties)
        requestHeaders.putAll(BilibiliHttpSupport.headers())
        factory.setDefaultRequestProperties(requestHeaders)

        val startedMs = SystemClock.elapsedRealtime()
        val mirrors = BilibiliMirrors.groupFor(dataSpec.uri.toString())
        if (mirrors == null) BilibiliHttpSupport.warnIfNoMirrors(dataSpec.uri.host)
        return try {
            val length: Long
            var openedUri = dataSpec.uri
            var hedged = false
            if (mirrors != null) {
                val winner =
                    HedgedOpen.race(
                        urls = mirrors.order(),
                        hedgeDelayMs = BilibiliHttpSupport.HEDGE_DELAY_MS,
                        executor = BilibiliHttpSupport.hedgeExecutor,
                        open = { url ->
                            val source = factory.createDataSource()
                            try {
                                OpenedSource(source, source.open(dataSpec.withUri(Uri.parse(url))))
                            } catch (e: Throwable) {
                                runCatching { source.close() }
                                throw e
                            }
                        },
                        close = { runCatching { it.source.close() } },
                    )
                mirrors.markWinner(winner.url)
                dataSource = winner.value.source
                length = winner.value.length
                openedUri = Uri.parse(winner.url)
                hedged = winner.hedged
            } else {
                dataSource = factory.createDataSource()
                length = dataSource!!.open(dataSpec)
            }
            currentUri = openedUri
            speedUrl = openedUri.toString()
            // Only the requests worth a look: one that needed its other mirror, or that took long
            // to answer. The rest is the normal case and would bury these.
            val answeredMs = SystemClock.elapsedRealtime() - startedMs
            if (hedged || answeredMs >= BilibiliHttpSupport.SLOW_OPEN_LOG_MS) {
                Log.w(
                    "BiliCdn",
                    "open host=${openedUri.host} pos=${dataSpec.position} reqLen=${dataSpec.length} " +
                        "answeredInMs=$answeredMs mirrored=${mirrors != null} hedged=$hedged",
                )
            }
            length
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 403) logForbidden(dataSpec)
            throw e
        } catch (e: java.io.IOException) {
            // A cancelled request (the player seeked away) is not a failure; anything else is.
            if (e !is java.io.InterruptedIOException && e.cause !is java.io.InterruptedIOException) {
                Log.w(
                    "BiliCdn",
                    "open FAILED host=${dataSpec.uri.host} pos=${dataSpec.position} " +
                        "afterMs=${SystemClock.elapsedRealtime() - startedMs} ${e::class.java.simpleName}: ${e.message}",
                )
            }
            throw e
        }
    }

    private fun logForbidden(dataSpec: DataSpec) {
        val url = dataSpec.uri.toString()
        val expiry = StreamDenialClassifier.describeExpiry(url)
        val kind = StreamDenialClassifier.classify(url)
        val client = StreamDenialClassifier.clientOf(url)
        val itag = StreamDenialClassifier.itagOf(url)
        val pot = StreamDenialClassifier.hasPoToken(url)
        Log.w(
            TAG,
            "HTTP 403 isBili=true c=$client itag=$itag mime=${StreamDenialClassifier.queryParam(url, "mime")} " +
                "pot=$pot range=${dataSpec.position}+${dataSpec.length} $expiry denial=$kind",
        )
        PlayerDiagnostics.logWarning(
            TAG,
            "403 isBili=true c=$client itag=$itag pot=$pot range=${dataSpec.position}+${dataSpec.length} $expiry denial=$kind",
        )
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val source = dataSource ?: return C.RESULT_END_OF_INPUT
        if (speedUrl == null) return source.read(buffer, offset, length)
        val startedNanos = System.nanoTime()
        val count = source.read(buffer, offset, length)
        readNanos += System.nanoTime() - startedNanos
        if (count > 0) readBytes += count
        return count
    }

    override fun close() {
        speedUrl?.let { BilibiliMirrors.recordSpeed(it, readBytes, readNanos) }
        speedUrl = null
        dataSource?.close()
        dataSource = null
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseCode(): Int = (dataSource as? HttpDataSource)?.responseCode ?: -1

    override fun getResponseHeaders(): Map<String, List<String>> = (dataSource as? HttpDataSource)?.responseHeaders ?: emptyMap()

    override fun clearAllRequestProperties() {}

    override fun clearRequestProperty(name: String) {}

    override fun setRequestProperty(
        name: String,
        value: String,
    ) {}

    private companion object {
        const val TAG = "YouTubeHttpDataSource"
    }
}
