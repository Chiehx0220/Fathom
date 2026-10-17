package io.github.aedev.flow.bilibili

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Stream CDNs: hosts, required headers, mirror trust. */
object BilibiliCdn {
    private const val ORIGIN = "https://www.bilibili.com"

    private val CDN_HOSTS = listOf("akamaized.net", "bilivideo.com", "bilivideo.cn", "mountaintoys.cn")

    /** 403 unless Referer is bilibili.com. */
    fun headers(): Map<String, String> = mapOf("Origin" to ORIGIN, "Referer" to "$ORIGIN/")

    fun isCdnHost(host: String): Boolean = CDN_HOSTS.any { it in host }

    fun isCdnUrl(url: String): Boolean = url.toHttpUrlOrNull()?.host?.let(::isCdnHost) == true

    /** M-CDN (PCDN) nodes `*.mcdn.bilivideo.cn`, `*.edge.mountaintoys.cn`: no HEAD, 403 for non-browser clients, short-lived signatures. */
    fun isMcdnUrl(url: String): Boolean = "mcdn.bilivideo" in url || "mountaintoys" in url || "os=mcdn" in url

    /** [primary] + [backups] minus M-CDN; [primary] alone if all are M-CDN. */
    fun stableUrls(
        primary: String,
        backups: List<String>,
    ): List<String> = (listOf(primary) + backups).filter { it.isNotEmpty() && !isMcdnUrl(it) }.distinct().ifEmpty { listOf(primary) }
}
