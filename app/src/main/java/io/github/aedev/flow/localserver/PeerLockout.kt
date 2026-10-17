package io.github.aedev.flow.localserver

/** Misses per peer; [maxMisses] lock the peer for [lockMs]. Not thread-safe. */
internal class PeerLockout(
    private val maxMisses: Int,
    private val lockMs: Long,
    private val maxPeers: Int,
    private val now: () -> Long,
) {
    private class Record(
        var misses: Int = 0,
        var lockedUntil: Long = 0L,
    )

    private val records = HashMap<String, Record>()

    fun remainingMs(peer: String): Long = ((records[peer]?.lockedUntil ?: 0L) - now()).coerceAtLeast(0L)

    fun recordMiss(peer: String) {
        val time = now()
        if (records.size > maxPeers) records.values.removeAll { it.lockedUntil <= time }
        val record = records.getOrPut(peer) { Record() }
        if (++record.misses >= maxMisses) {
            record.misses = 0
            record.lockedUntil = time + lockMs
        }
    }

    fun clear(peer: String) {
        records.remove(peer)
    }

    fun clear() = records.clear()
}
