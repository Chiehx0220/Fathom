package io.github.aedev.flow.sync2.engine

import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync2.changes.IncomingBatch
import io.github.aedev.flow.sync2.changes.parseBatch
import io.github.aedev.flow.sync2.crypto.ProtocolError
import io.github.aedev.flow.sync2.crypto.decodePayload
import io.github.aedev.flow.sync2.crypto.openBlob
import io.github.aedev.flow.sync2.crypto.purposeKey
import io.github.aedev.flow.sync2.crypto.reject
import io.github.aedev.flow.sync2.store.ObjectType
import io.github.aedev.flow.sync2.store.StorePaths
import io.github.aedev.flow.sync2.store.StreamPosition

/** The snapshot a device starts from: its writer, its records, and where each log goes on. */
internal class Start(
    val writer: String,
    val manifest: Manifest,
    val batches: List<IncomingBatch>,
    val positions: Map<String, StreamPosition>,
)

/** How many objects of all logs the snapshot includes. */
private fun Manifest.merged(): Long = vv.values.sumOf { it.seq }

/** Whether the manifest covers every log that no longer starts at its first object. */
private fun Manifest.covers(first: Map<String, Long>): Boolean =
    first.filterValues { it > 1 }.all { (did, seq) ->
        (vv[did]?.seq ?: -1) + 1 >=
            seq
    }

/**
 * Where a device that has read nothing starts (spec/protocol.md, Snapshots): `null` when every log
 * still starts at its first object, or when no snapshot covers the logs that don't. Each log then
 * goes on from the object the snapshot names, chained to its hash.
 */
internal suspend fun Session.bootstrap(): Start? {
    val logs = HashMap<String, MutableList<Long>>()
    var cursor = 0L
    while (true) {
        val (changes, next) = store.changes(cursor)
        if (changes.isEmpty()) break
        changes.mapNotNull { StorePaths.parseLog(it.path) }.forEach { (did, seq) -> logs.getOrPut(did) { ArrayList() } += seq }
        cursor = next
    }
    logs.remove(me)
    val first = logs.mapValues { (_, seqs) -> seqs.min() }
    if (first.values.all { it == 1L }) return null

    var best: Triple<String, Int, Manifest>? = null
    for ((did, seqs) in logs) {
        val (epoch, manifest) = newestManifest(did, seqs.sorted()) ?: continue
        if (manifest.covers(first) && (best == null || best.third.merged() < manifest.merged())) best = Triple(did, epoch, manifest)
    }
    val (writer, epoch, manifest) = best ?: return null
    val positions =
        manifest.vv.filterKeys { it != me }.mapValues { (_, tip) ->
            val hash = runCatching { SyncBytes.fromHex(tip.hash) }.getOrNull()?.takeIf { it.size == 32 } ?: reject(ProtocolError.PREV)
            StreamPosition(tip.seq, hash)
        }
    return Start(writer, manifest, snapshotBatches(writer, epoch, manifest), positions)
}

/** The newest snapshot manifest in [did]'s log, searched from its last object back, and the epoch it was written in. */
private suspend fun Session.newestManifest(
    did: String,
    seqs: List<Long>,
): Pair<Int, Manifest>? {
    for (seq in seqs.asReversed()) {
        val parsed = verified(store.getObject(StorePaths.log(did, seq)) ?: continue, did, seq)
        if (parsed.header.type != ObjectType.SNAPSHOT) continue
        val content = openContent(parsed) as Content.Snapshot
        return parsed.header.epoch to content.manifest
    }
    return null
}

private suspend fun Session.snapshotBatches(
    writer: String,
    epoch: Int,
    manifest: Manifest,
): List<IncomingBatch> {
    val key = purposeKey(epochKey(epoch), group, epoch, ObjectType.SNAPSHOT.purpose)
    return manifest.parts.map { part ->
        val blob = store.getObject(StorePaths.blob(writer, part.blob)) ?: reject(ProtocolError.BLOB_HASH)
        val hash = runCatching { SyncBytes.fromHex(part.blob) }.getOrNull() ?: reject(ProtocolError.BLOB_HASH)
        parseBatch(decodePayload(openBlob(key, group, epoch, SyncBytes.fromHex(writer), blob, hash)))
    }
}
