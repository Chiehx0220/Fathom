package io.github.aedev.flow.data.shorts.feed

enum class ShortsSeedSource {
    LIKED,
    WANT_MORE,
    SAVED,
    WATCHED,

    /** Dwelled on in this session; weakest source. */
    FEED,
}

/** Candidate seed of a related-reel chain. */
data class ShortsSeedInput(
    val id: String,
    val channelId: String,
    val source: ShortsSeedSource,
    val timestamp: Long,
    /** Read for [ShortsSeedSource.WATCHED] only. */
    val percentWatched: Double = 100.0,
)

/** Seed selection: source weight x recency, round-robin over channels up to `maxPerChannel`. */
internal object ShortsSeedSelector {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MIN_WATCHED_PERCENT = 60.0
    private const val FULL_WATCH_PERCENT = 90.0

    private class Ranked(
        val id: String,
        val channel: String,
        val weight: Double,
    )

    fun select(
        candidates: List<ShortsSeedInput>,
        maxSeeds: Int,
        now: Long = System.currentTimeMillis(),
        excludedChannelIds: Set<String> = emptySet(),
        cooldownIds: Set<String> = emptySet(),
        maxPerChannel: Int = 1,
    ): List<String> {
        if (candidates.isEmpty() || maxSeeds <= 0) return emptyList()
        val eligible = candidates.filter { it.isEligible(excludedChannelIds) }
        val rotated = eligible.filterNot { it.id in cooldownIds }
        val ranked =
            (if (rotated.size >= maxSeeds) rotated else eligible)
                .map { Ranked(it.id, it.channelId.ifBlank { it.id }, it.sourceWeight() * recencyWeight(it.timestamp, now)) }
                .groupBy { it.id }
                .values
                .map { group -> group.maxBy { it.weight } }
                .sortedByDescending { it.weight }
        val picked = LinkedHashSet<String>()
        val perChannel = HashMap<String, Int>()
        for (allowed in 1..maxPerChannel.coerceAtLeast(1)) {
            for (seed in ranked) {
                if (picked.size >= maxSeeds) return picked.toList()
                if (seed.id in picked || (perChannel[seed.channel] ?: 0) >= allowed) continue
                picked += seed.id
                perChannel.merge(seed.channel, 1, Int::plus)
            }
        }
        return picked.toList()
    }

    private fun ShortsSeedInput.isEligible(excludedChannelIds: Set<String>): Boolean =
        id.isNotBlank() &&
            (channelId.isBlank() || channelId !in excludedChannelIds) &&
            (source != ShortsSeedSource.WATCHED || percentWatched >= MIN_WATCHED_PERCENT)

    private fun ShortsSeedInput.sourceWeight(): Double =
        when (source) {
            ShortsSeedSource.WANT_MORE -> 1.5
            ShortsSeedSource.LIKED -> 1.4
            ShortsSeedSource.WATCHED -> if (percentWatched >= FULL_WATCH_PERCENT) 1.2 else 0.8
            ShortsSeedSource.SAVED -> 1.0
            ShortsSeedSource.FEED -> 0.7
        }

    private fun recencyWeight(
        timestamp: Long,
        now: Long,
    ): Double {
        if (timestamp <= 0L) return 0.85
        val ageDays = ((now - timestamp).coerceAtLeast(0L) / DAY_MS).toInt()
        return when {
            ageDays <= 1 -> 1.0
            ageDays <= 7 -> 0.9
            ageDays <= 30 -> 0.75
            ageDays <= 90 -> 0.55
            else -> 0.4
        }
    }
}
