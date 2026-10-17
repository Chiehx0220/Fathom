package io.github.aedev.flow.player.datasource

import android.net.Uri
import android.util.Log
import io.github.aedev.flow.bilibili.BilibiliCdn
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bilibili behavior for [BilibiliRoutingDataSource], [BilibiliHttpDataSource]: CDN host detection, request headers, mirror-hedging tuning.
 * Outside [YouTubeHttpDataSource]: upstream edits merge cleanly.
 */
object BilibiliHttpSupport {
    // Mirror silent this long is raced against the next. Measured: ~0.2s median, 2-7s slow tail: normal case untouched, tail cut.
    const val HEDGE_DELAY_MS = 600L

    // Opens no faster than this logged even if not hedged.
    const val SLOW_OPEN_LOG_MS = 1_000L

    val hedgeExecutor: ExecutorService =
        Executors.newCachedThreadPool { runnable -> Thread(runnable, "bili-hedge").apply { isDaemon = true } }

    // Matches desktop UA of the verified localserver Bilibili CDN proxy (LocalHttpServer.kt), not player default mobile UA
    // (unconfirmed whether Akamai edges check UA shape).
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val warnedNoMirrors = AtomicBoolean(false)

    fun isBilibiliCdnUri(uri: Uri): Boolean = uri.host?.let(BilibiliCdn::isCdnHost) == true

    // No Cookie header: local server CDN proxy sends User-Agent/Referer/Origin only; adding Cookie still 403'd.
    fun headers(): Map<String, String> = BilibiliCdn.headers()

    fun warnIfNoMirrors(host: String?) {
        if (warnedNoMirrors.compareAndSet(false, true)) {
            Log.w("BiliCdn", "no mirror group registered for host=$host; requests are not hedged")
        }
    }
}
