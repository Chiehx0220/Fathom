package io.github.aedev.flow.player.datasource

/**
 * The mirror URLs of each Bilibili stream (its own CDN and Akamai). The player holds one URL per
 * stream, so [YouTubeHttpDataSource] looks the others up here, and which one answered first last time.
 */
object BilibiliMirrors {
    /** The mirrors of one stream, in the order they were listed, plus the one that last won. */
    class Group internal constructor(
        val urls: List<String>,
    ) {
        @Volatile
        private var preferred = 0

        /** Mirrors in the order to try them: the last winner first, the rest after it. */
        fun order(): List<String> {
            val start = preferred
            return urls.indices.map { urls[(start + it) % urls.size] }
        }

        fun markWinner(url: String) {
            val index = urls.indexOf(url)
            if (index >= 0) preferred = index
        }
    }

    private const val MAX_GROUPS = 1024

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

    internal fun clearForTest() = synchronized(groups) { groups.clear() }
}
