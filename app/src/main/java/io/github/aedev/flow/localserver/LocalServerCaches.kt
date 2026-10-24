package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.util.concurrent.TimeUnit

/** Process-wide proxy-handler caches + fetch HTTP client. */
internal object LocalServerCaches {
    internal val streamUrlCache = StreamUrlCache()

    // InnerTube client that minted each cached URL: googlevideo 403s on UA/client mismatch.
    internal val streamUaCache = StreamUrlCache()

    // One extraction per video, shared by watch + manifest handlers. Smaller than streamUrlCache (parsed extractor state).
    internal val extractorCache = ExtractorCache()
    internal val httpClient: okhttp3.OkHttpClient =
        okhttp3.OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
}

private class CacheData(
    val value: String?,
    timeoutMillis: Long,
) {
    val expireTimestamp: Long = System.currentTimeMillis() + timeoutMillis

    fun isExpired(): Boolean = System.currentTimeMillis() > expireTimestamp
}

internal class StreamUrlCache {
    companion object {
        private const val MAX_ITEMS = 60
    }

    private val map =
        object : LinkedHashMap<String, CacheData>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheData>?): Boolean = size > MAX_ITEMS
        }

    @Synchronized
    fun get(key: String): String? {
        val data = map[key]
        if (data == null) {
            return null
        }
        if (data.isExpired()) {
            map.remove(key)
            return null
        }
        return data.value
    }

    @Synchronized
    fun put(
        key: String,
        value: String?,
        timeoutMillis: Long,
    ) {
        removeStale()
        map[key] = CacheData(value, timeoutMillis)
    }

    @Synchronized
    fun remove(key: String) {
        map.remove(key)
    }

    private fun removeStale() {
        map.entries.removeIf { it.value.isExpired() }
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}

internal class ExtractorCacheData(
    val value: StreamExtractor,
    timeoutMillis: Long,
) {
    val expireTimestamp: Long = System.currentTimeMillis() + timeoutMillis

    fun isExpired(): Boolean = System.currentTimeMillis() > expireTimestamp
}

internal class ExtractorCache {
    companion object {
        private const val MAX_ITEMS = 15
    }

    private val map =
        object : LinkedHashMap<String, ExtractorCacheData>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExtractorCacheData>?): Boolean = size > MAX_ITEMS
        }

    @Synchronized
    fun get(key: String): StreamExtractor? {
        val data = map[key]
        if (data == null) {
            return null
        }
        if (data.isExpired()) {
            map.remove(key)
            return null
        }
        return data.value
    }

    @Synchronized
    fun put(
        key: String,
        value: StreamExtractor,
        timeoutMillis: Long,
    ) {
        map.entries.removeIf { it.value.isExpired() }
        map[key] = ExtractorCacheData(value, timeoutMillis)
    }
}
