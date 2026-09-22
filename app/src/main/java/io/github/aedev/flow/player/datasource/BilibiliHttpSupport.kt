package io.github.aedev.flow.player.datasource

import android.net.Uri
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bilibili-specific behavior for [YouTubeHttpDataSource]: CDN host detection, request headers and
 * mirror-hedging tuning. Kept out of that class so upstream's own edits to it stay a clean merge.
 */
object BilibiliHttpSupport {
    // A mirror that has not answered in this long is raced against the next one. Measured
    // requests answered in ~0.2s at the median and 2-7s in the slow tail, so this leaves the
    // normal case alone and cuts the tail short.
    const val HEDGE_DELAY_MS = 600L

    // Opens that answered no faster than this are logged even when not hedged.
    const val SLOW_OPEN_LOG_MS = 1_000L

    val hedgeExecutor: ExecutorService =
        Executors.newCachedThreadPool { runnable -> Thread(runnable, "bili-hedge").apply { isDaemon = true } }

    // Matches the desktop UA the already-verified-working localserver Bilibili CDN proxy uses
    // (see LocalHttpServer.kt) — kept identical rather than the player's default mobile UA,
    // since it's unconfirmed whether Bilibili's Akamai edges care about the UA shape.
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val warnedNoMirrors = AtomicBoolean(false)

    // Broader than BilibiliService.isBiliBiliDownloadUrl() in the extractor (which only checks
    // "bilivideo.com"/"akamaized.net"): Bilibili also serves from *.mcdn.bilivideo.cn edge/P2P
    // mirrors (note the .cn, not .com), so match on "bilivideo" generally to catch those too.
    fun isBilibiliCdnUri(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.contains("bilivideo") || host.contains("akamaized.net")
    }

    // These CDN edges hotlink-check Referer (and, for some streams, the session cookie) and 403
    // without it. Deliberately no Cookie header — matches the already-verified-working localserver
    // Bilibili CDN proxy, which sends only User-Agent/Referer/Origin; an earlier attempt that added
    // a Cookie here still 403'd, so it's left out.
    fun headers(): Map<String, String> =
        mapOf(
            "Origin" to "https://www.bilibili.com",
            "Referer" to "https://www.bilibili.com/",
        )

    fun warnIfNoMirrors(host: String?) {
        if (warnedNoMirrors.compareAndSet(false, true)) {
            Log.w("BiliCdn", "no mirror group registered for host=$host; requests are not hedged")
        }
    }
}
