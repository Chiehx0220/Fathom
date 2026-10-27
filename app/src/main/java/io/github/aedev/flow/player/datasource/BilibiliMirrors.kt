package io.github.aedev.flow.player.datasource

/**
 * Mirror URLs per stream (own CDN, Akamai); fastest host first ([recordSpeed]), last first-answerer breaks ties.
 */
object BilibiliMirrors {
    /** Mirrors of one stream in listed order + last winner. */
    class Group internal constructor(
        val urls: List<String>,
    ) {
        @Volatile
        private var preferred = 0

        /** Mirrors in try order: fastest measured host first; unmeasured before measured; last winner first among equals. */
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

    // Sample = bytes / time waiting inside reads: network rate, not player consumption rate. Short transfers ignored.
    private const val MIN_SAMPLE_BYTES = 128L * 1024
    private const val MIN_SAMPLE_NANOS = 100_000_000L
    private const val SMOOTHING = 0.5

    // Older: host speed stale, remeasured on next try.
    private const val SPEED_TTL_MS = 90_000L

    private class Speed(
        val bytesPerSecond: Double,
        val updatedMs: Long,
    )

    private val speeds = HashMap<String, Speed>()

    private fun hostOf(url: String): String? = runCatching { java.net.URI(url).host }.getOrNull()

    /** Folds a finished transfer ([bytes] over [readNanos]) into [url] host speed. */
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

    /** Bytes/s last measured on [url] host; null if no fresh measurement. */
    fun speedOf(
        url: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Double? {
        val host = hostOf(url) ?: return null
        val speed = synchronized(speeds) { speeds[host] } ?: return null
        return speed.bytesPerSecond.takeIf { nowMs - speed.updatedMs <= SPEED_TTL_MS }
    }

    // Access-ordered: streams being read stay, old videos age out.
    private val groups =
        object : LinkedHashMap<String, Group>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Group>?): Boolean = size > MAX_GROUPS
        }

    /** Registers a stream's [primary] URL + [backups]; no backup, no group. */
    fun register(
        primary: String,
        backups: List<String>,
    ) {
        val urls = (listOf(primary) + backups).filter { it.isNotEmpty() }.distinct()
        if (urls.size < 2) return
        val group = Group(urls)
        synchronized(groups) { urls.forEach { groups[it] = group } }
    }

    /** Group of [url] (any of its mirrors); null if no other mirror. */
    fun groupFor(url: String): Group? = synchronized(groups) { groups[url] }

    internal fun clearForTest() {
        synchronized(groups) { groups.clear() }
        synchronized(speeds) { speeds.clear() }
    }
}
