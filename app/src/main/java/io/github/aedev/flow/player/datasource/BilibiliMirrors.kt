package io.github.aedev.flow.player.datasource

/**
 * The mirror URLs of each Bilibili stream (its own CDN and Akamai). The player holds one URL per
 * stream, so [YouTubeHttpDataSource] looks the others up here. Mirrors are tried fastest first, by the
 * transfer speed measured on each host ([recordSpeed]); which one answered first last time breaks ties.
 */
object BilibiliMirrors {
    /** The mirrors of one stream, in the order they were listed, plus the one that last won. */
    class Group internal constructor(
        val urls: List<String>,
    ) {
        @Volatile
        private var preferred = 0

        /**
         * Mirrors in the order to try them: fastest measured host first. A host with no fresh measurement
         * goes ahead of measured ones so that it gets measured, and the last winner comes first among equals.
         */
        fun order(nowMs: Long = System.currentTimeMillis()): List<String> {
            val start = preferred
            return urls.indices
                .map { urls[(start + it) % urls.size] }
                .sortedByDescending { speedOf(it, nowMs) ?: Double.MAX_VALUE }
        }

        fun markWinner(url: String) {
            val index = urls.indexOf(url)
            if (index >= 0) preferred = index
        }
    }

    private const val MAX_GROUPS = 1024

    // A sample is bytes read over the time spent waiting inside reads, so it is the network's rate
    // and not how fast the player chose to consume. Short transfers say little and are ignored.
    private const val MIN_SAMPLE_BYTES = 128L * 1024
    private const val MIN_SAMPLE_NANOS = 100_000_000L
    private const val SMOOTHING = 0.5

    // Older than this a host's speed is stale, and the host is measured again the next time it is tried.
    private const val SPEED_TTL_MS = 90_000L

    private class Speed(
        val bytesPerSecond: Double,
        val updatedMs: Long,
    )

    private val speeds = HashMap<String, Speed>()

    private fun hostOf(url: String): String? = runCatching { java.net.URI(url).host }.getOrNull()

    /** Folds one finished transfer of [bytes] read over [readNanos] into the speed of [url]'s host. */
    fun recordSpeed(
        url: String,
        bytes: Long,
        readNanos: Long,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (bytes < MIN_SAMPLE_BYTES || readNanos < MIN_SAMPLE_NANOS) return
        val host = hostOf(url) ?: return
        val sample = bytes * 1e9 / readNanos
        synchronized(speeds) {
            val previous = speeds[host]
            val fresh = previous != null && nowMs - previous.updatedMs <= SPEED_TTL_MS
            val smoothed = if (fresh) previous!!.bytesPerSecond * (1 - SMOOTHING) + sample * SMOOTHING else sample
            speeds[host] = Speed(smoothed, nowMs)
        }
    }

    /** Bytes per second last measured on [url]'s host, or null when it has no fresh measurement. */
    fun speedOf(
        url: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Double? {
        val host = hostOf(url) ?: return null
        val speed = synchronized(speeds) { speeds[host] } ?: return null
        return speed.bytesPerSecond.takeIf { nowMs - speed.updatedMs <= SPEED_TTL_MS }
    }

    // Access-ordered so the streams a player is actually reading stay and old videos age out.
    private val groups =
        object : LinkedHashMap<String, Group>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Group>?): Boolean = size > MAX_GROUPS
        }

    /** Registers one stream's [primary] URL and its [backups]; a stream with no backup needs no group. */
    fun register(
        primary: String,
        backups: List<String>,
    ) {
        val urls = (listOf(primary) + backups).filter { it.isNotEmpty() }.distinct()
        if (urls.size < 2) return
        val group = Group(urls)
        synchronized(groups) { urls.forEach { groups[it] = group } }
    }

    /** The group [url] belongs to, whichever of its mirrors [url] is; null when it has no other mirror. */
    fun groupFor(url: String): Group? = synchronized(groups) { groups[url] }

    internal fun clearForTest() {
        synchronized(groups) { groups.clear() }
        synchronized(speeds) { speeds.clear() }
    }
}
