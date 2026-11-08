package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.util.concurrent.TimeUnit

/** Process-wide proxy-handler caches + fetch HTTP client. */
internal object LocalServerCaches {
    internal val streamUrlCache = TtlCache<String>(maxItems = 60)

    // InnerTube client that minted each cached URL: googlevideo 403s on UA/client mismatch.
    internal val streamUaCache = TtlCache<String>(maxItems = 60)

    // One extraction per video for watch + manifest handlers; smaller than streamUrlCache.
    internal val extractorCache = TtlCache<StreamExtractor>(maxItems = 15)
    internal val httpClient: okhttp3.OkHttpClient =
        okhttp3.OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
}

/** LRU with per-entry expiry; synchronized. */
internal class TtlCache<V : Any>(
    private val maxItems: Int,
) {
    private class Entry<V>(
        val value: V,
        ttlMillis: Long,
    ) {
        private val expiresAt = System.currentTimeMillis() + ttlMillis

        fun isExpired() = System.currentTimeMillis() > expiresAt
    }

    private val map =
        object : LinkedHashMap<String, Entry<V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<V>>?) = size > maxItems
        }

    @Synchronized
    fun get(key: String): V? {
        val entry = map[key] ?: return null
        if (entry.isExpired()) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(
        key: String,
        value: V,
        ttlMillis: Long,
    ) {
        map.entries.removeIf { it.value.isExpired() }
        map[key] = Entry(value, ttlMillis)
    }

    @Synchronized
    fun remove(key: String) {
        map.remove(key)
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}
