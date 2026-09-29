package io.github.aedev.flow.bilibili

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Bilibili's stream CDNs: which hosts they are, the headers they require and which mirrors to trust. */
object BilibiliCdn {
    private const val ORIGIN = "https://www.bilibili.com"

    private val CDN_HOSTS = listOf("akamaized.net", "bilivideo.com", "bilivideo.cn", "mountaintoys.cn")

    /** The CDNs answer 403 unless the request appears to come from bilibili.com. */
    fun headers(): Map<String, String> = mapOf("Origin" to ORIGIN, "Referer" to "$ORIGIN/")

    fun isCdnHost(host: String): Boolean = CDN_HOSTS.any { it in host }

    fun isCdnUrl(url: String): Boolean = url.toHttpUrlOrNull()?.host?.let(::isCdnHost) == true

    /**
     * M-CDN (PCDN) edge nodes, `*.mcdn.bilivideo.cn` and `*.edge.mountaintoys.cn`. They are built for
     * the web player: no HEAD support, 403 for non-browser clients and short-lived signatures.
     */
    fun isMcdnUrl(url: String): Boolean = "mcdn.bilivideo" in url || "mountaintoys" in url || "os=mcdn" in url

    /** [primary] then [backups] without M-CDN nodes; the primary alone when every candidate is one. */
    fun stableUrls(
        primary: String,
        backups: List<String>,
    ): List<String> = (listOf(primary) + backups).filter { it.isNotEmpty() && !isMcdnUrl(it) }.distinct().ifEmpty { listOf(primary) }
}
