package io.github.aedev.flow.sync2.store

import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync2.crypto.ProtocolError
import io.github.aedev.flow.sync2.crypto.reject
import io.github.aedev.flow.sync2.data.HeldDao
import io.github.aedev.flow.sync2.data.HeldRow
import io.github.aedev.flow.sync2.roster.Entry
import io.github.aedev.flow.sync2.roster.RosterState
import io.github.aedev.flow.sync2.roster.applyEntry
import io.github.aedev.flow.sync2.roster.canonicalEntry
import io.github.aedev.flow.sync2.roster.entryHash
import io.github.aedev.flow.sync2.roster.forkWinner
import io.github.aedev.flow.sync2.roster.parseEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

private const val PAGE = 500

/**
 * What this device holds of its group, in the store layout (spec/store.md): every roster entry it has
 * seen, fork candidates included, the wraps and chain boxes, and the log objects and blobs. Sessions
 * read it; the server, if any, and LAN peers fill it.
 */
class LocalStore(
    private val root: File,
    private val held: HeldDao,
) : Store {
    private fun file(path: String) = File(root, path)

    private fun read(path: String): ByteArray? = file(path).takeIf { it.isFile }?.readBytes()

    /** Writes a file once; an existing one is kept, as no store overwrites. */
    private fun write(
        path: String,
        bytes: ByteArray,
    ): Boolean {
        val target = file(path)
        if (target.exists()) return false
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        return temp.renameTo(target)
    }

    /** Every roster entry held with a `seq` above [after], fork candidates included. */
    suspend fun entries(after: Long = -1): List<Entry> =
        withContext(Dispatchers.IO) {
            file("roster")
                .listFiles()
                .orEmpty()
                .filter { it.name.endsWith(".r") }
                .mapNotNull { runCatching { parseEntry(Json.parseToJsonElement(it.readText())) }.getOrNull() }
                .filter { it.seq > after }
                .sortedBy { it.seq }
        }

    /** The chain every device picks: at each `seq`, the winner among the entries valid there (spec/protocol.md, Forks). */
    private suspend fun resolved(): List<Entry> {
        val chain = ArrayList<Entry>()
        var state: RosterState? = null
        for ((_, candidates) in entries().groupBy { it.seq }.toSortedMap()) {
            val winner = forkWinner(candidates.filter { runCatching { applyEntry(state, it) }.isSuccess }) ?: break
            state = applyEntry(state, winner)
            chain += winner
        }
        return chain
    }

    suspend fun addEntries(entries: List<Entry>) =
        withContext(Dispatchers.IO) {
            entries.forEach { write(StorePaths.roster(it.seq, entryHash(it)), canonicalEntry(it).toByteArray()) }
        }

    suspend fun putKey(
        epoch: Int,
        name: String,
        bytes: ByteArray,
    ) {
        withContext(Dispatchers.IO) { write(StorePaths.key(epoch, name), bytes) }
    }

    /** Holds an entry with its wraps and chain box, wraps first (spec/store.md, Writing rules). */
    suspend fun hold(
        entry: Entry,
        epoch: Int,
        wraps: Map<String, ByteArray>,
        chain: ByteArray?,
    ) {
        wraps.forEach { (did, wrap) -> putKey(epoch, did, wrap) }
        chain?.let { putKey(epoch, "chain", it) }
        addEntries(listOf(entry))
    }

    override suspend fun rosterAfter(after: Long): List<Entry> = resolved().filter { it.seq > after }

    override suspend fun appendRoster(
        head: String,
        entry: Entry,
        wraps: Map<String, String>,
        chain: String?,
    ): Appended {
        val current = resolved()
        val currentHead = current.lastOrNull()?.let(::entryHash).orEmpty()
        if (currentHead != head) return Appended.Conflict(currentHead)
        val epoch = entry.epoch?.n ?: current.fold(null as RosterState?) { state, e -> applyEntry(state, e) }?.epoch ?: 0
        hold(entry, epoch, wraps.mapValues { (_, wrap) -> decode(wrap) }, chain?.let(::decode))
        return Appended.Done
    }

    override suspend fun key(
        epoch: Int,
        name: String,
    ): ByteArray? = withContext(Dispatchers.IO) { read(StorePaths.key(epoch, name)) }

    override suspend fun putObject(
        path: String,
        bytes: ByteArray,
    ) {
        require(StorePaths.isObject(path)) { "Not an object path" }
        withContext(Dispatchers.IO) { write(path, bytes) }
        held.add(HeldRow(path = path, size = bytes.size.toLong()))
    }

    override suspend fun getObject(path: String): ByteArray? =
        if (StorePaths.isObject(path)) withContext(Dispatchers.IO) { read(path) } else null

    override suspend fun deleteObject(path: String) {
        if (!StorePaths.isObject(path)) return
        withContext(Dispatchers.IO) { file(path).delete() }
        held.remove(path)
    }

    override suspend fun changes(cursor: Long): Pair<List<ChangedObject>, Long> {
        val rows = held.after(cursor, PAGE)
        return rows.map { ChangedObject(it.path, it.size) } to (rows.lastOrNull()?.cursor ?: cursor)
    }

    /** Forgets everything held, when this device leaves its group; the index goes with the database's tables. */
    suspend fun clear() {
        withContext(Dispatchers.IO) { root.deleteRecursively() }
    }

    /** What a LAN peer asked for with [vv]: the log objects held above it, and the blobs held. */
    suspend fun list(vv: Map<String, Long>): Pair<List<String>, List<String>> {
        val paths = held.paths()
        val logs = paths.filter { path -> StorePaths.parseLog(path)?.let { (did, seq) -> seq > (vv[did] ?: 0) } == true }
        return logs to paths.filter { StorePaths.parseLog(it) == null }
    }

    private fun decode(b64: String): ByteArray =
        runCatching { SyncBytes.b64urlDecode(b64) }.getOrNull() ?: reject(ProtocolError.ROSTER_FORMAT)
}
