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
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.network.AppProxyManager
import io.github.aedev.flow.player.error.PlayerDiagnostics
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * YouTube-specific HttpDataSource optimized for streaming performance.
 *
 * Key optimizations:
 * - Longer timeouts (30s read) to handle YouTube's variable latency
 * - Proper YouTube headers to avoid bot detection
 * - Range parameter handling for DASH manifests
 * - Cross-protocol redirect support
 */
@UnstableApi
class YouTubeHttpDataSource private constructor(
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String>,
) : BaseDataSource(true),
    HttpDataSource {
    private var dataSource: DataSource? = null
    private var currentUri: Uri? = null

    class Factory : HttpDataSource.Factory {
        private val requestProperties = HashMap<String, String>()
        private var userAgent =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        override fun createDataSource(): HttpDataSource = YouTubeHttpDataSource(userAgent, requestProperties.toMap())

        override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
            requestProperties.clear()
            requestProperties.putAll(defaultRequestProperties)
            return this
        }
    }

    private class OpenedSource(
        val source: DataSource,
        val length: Long,
    )

    companion object {
        private const val TAG = "YouTubeHttpDataSource"

        private val clientLock = Any()

        @Volatile
        private var cachedClient: OkHttpClient? = null

        @Volatile
        private var cachedProxySignature: String = ""

        private fun sharedClient(): OkHttpClient {
            val proxySignature = AppProxyManager.currentSignature()
            cachedClient?.takeIf { cachedProxySignature == proxySignature }?.let { return it }

            return synchronized(clientLock) {
                cachedClient?.takeIf { cachedProxySignature == proxySignature } ?: run {
                    val client =
                        AppProxyManager
                            .applyTo(OkHttpClient.Builder())
                            .connectTimeout(15, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .followRedirects(true)
                            .followSslRedirects(true)
                            .retryOnConnectionFailure(true)
                            .build()
                    cachedProxySignature = proxySignature
                    cachedClient = client
                    client
                }
            }
        }
    }

    // The Bilibili transfer in progress: which URL, and what its reads have moved so far (see BilibiliMirrors.recordSpeed).
    private var speedUrl: String? = null
    private var readBytes = 0L
    private var readNanos = 0L

    @UnstableApi
    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        speedUrl = null
        readBytes = 0L
        readNanos = 0L

        val isBili = BilibiliHttpSupport.isBilibiliCdnUri(dataSpec.uri)
        val requestUserAgent =
            if (isYouTubeUri(dataSpec.uri)) {
                resolveYouTubeUserAgent(dataSpec.uri)
            } else if (isBili) {
                BilibiliHttpSupport.USER_AGENT
            } else {
                userAgent
            }
        val factory =
            OkHttpDataSource
                .Factory(sharedClient())
                .setUserAgent(requestUserAgent)

        val requestHeaders = LinkedHashMap<String, String>()
        requestHeaders.putAll(defaultRequestProperties)
        if (isYouTubeUri(dataSpec.uri)) {
            requestHeaders.putAll(youtubeHeaders())
        } else if (isBili) {
            requestHeaders.putAll(BilibiliHttpSupport.headers())
        }
        if (requestHeaders.isNotEmpty()) {
            factory.setDefaultRequestProperties(requestHeaders)
        }

        val startedMs = SystemClock.elapsedRealtime()
        // Bilibili lists two mirrors per file; when this request has one to fall back to, both may be
        // tried (see HedgedOpen) so a stalled mirror costs one short delay rather than seconds.
        val mirrors = if (isBili) BilibiliMirrors.groupFor(dataSpec.uri.toString()) else null
        if (isBili && mirrors == null) BilibiliHttpSupport.warnIfNoMirrors(dataSpec.uri.host)
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
            if (isBili) speedUrl = openedUri.toString()
            if (isBili) {
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
            }
            length
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            if (e.responseCode == 403) logForbidden(dataSpec, isBili, requestHeaders.keys)
            throw e
        } catch (e: java.io.IOException) {
            // A cancelled request (the player seeked away) is not a failure; anything else is.
            if (isBili && e !is java.io.InterruptedIOException && e.cause !is java.io.InterruptedIOException) {
                Log.w(
                    "BiliCdn",
                    "open FAILED host=${dataSpec.uri.host} pos=${dataSpec.position} " +
                        "afterMs=${SystemClock.elapsedRealtime() - startedMs} ${e::class.java.simpleName}: ${e.message}",
                )
            }
            throw e
        }
    }

    private fun logForbidden(
        dataSpec: DataSpec,
        isBili: Boolean,
        sentHeaderNames: Set<String>,
    ) {
        val uri = dataSpec.uri
        val expire = uri.getQueryParameter("expire")?.toLongOrNull()
        val nowSec = System.currentTimeMillis() / 1000
        val expiry =
            when {
                expire == null -> "expire=absent"
                expire < nowSec -> "expire=PASSED ${nowSec - expire}s ago"
                else -> "expire=valid ${expire - nowSec}s left"
            }
        // Temporary extra detail for diagnosing the Bilibili CDN 403 investigation: which host
        // actually 403'd, whether it was recognized as Bilibili, and which headers we sent.
        Log.w(
            TAG,
            "HTTP 403 host=${uri.host} isBili=$isBili sentHeaders=$sentHeaderNames " +
                "c=${uri.getQueryParameter("c")} itag=${uri.getQueryParameter("itag")} " +
                "mime=${uri.getQueryParameter("mime")} pot=${uri.getQueryParameter("pot") != null} " +
                "range=${dataSpec.position}+${dataSpec.length} $expiry",
        )
        PlayerDiagnostics.logWarning(
            TAG,
            "403 host=${uri.host} isBili=$isBili sentHeaders=$sentHeaderNames " +
                "c=${uri.getQueryParameter("c")} itag=${uri.getQueryParameter("itag")} " +
                "pot=${uri.getQueryParameter("pot") != null} range=${dataSpec.position}+${dataSpec.length} $expiry",
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

    private fun isYouTubeUri(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.contains("youtube.com") ||
            host.contains("googlevideo.com") ||
            host.contains("ytimg.com")
    }

    // The fetching UA must match the client that minted the URL (`c=` param) — a mismatch is a
    // known cause of mid-stream 403s on googlevideo CDNs.
    private fun resolveYouTubeUserAgent(uri: Uri): String =
        when (uri.getQueryParameter("c")?.uppercase()) {
            "IOS" -> YouTubeClient.IPADOS.userAgent
            "ANDROID", "ANDROID_CREATOR" -> YouTubeClient.ANDROID.userAgent
            "ANDROID_VR" -> YouTubeClient.ANDROID_VR_1_61_48.userAgent
            "VISIONOS" -> YouTubeClient.VISIONOS.userAgent
            "TVHTML5", "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER.userAgent
            "MWEB" -> YouTubeClient.USER_AGENT_MWEB
            "WEB", "WEB_REMIX" -> YouTubeClient.USER_AGENT_WEB
            else -> userAgent
        }

    /**
     * Add headers that YouTube expects/requires for video streaming.
     * These help avoid bot detection and ensure proper CDN routing.
     */
    private fun youtubeHeaders(): Map<String, String> =
        mapOf(
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            // Accept-Encoding helps with CDN optimization
            "Accept-Encoding" to "identity",
            // Accept header for video content
            "Accept" to "*/*",
        )
}
