package io.github.aedev.flow.sync2.linking

import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync.crypto.SyncCrypto
import io.github.aedev.flow.sync2.engine.LinkedException
import io.github.aedev.flow.sync2.store.ServerStore
import kotlinx.coroutines.delay

private const val POLL_MS = 2_000L
private const val NAME_LIMIT = 64

/**
 * The new device's side of joining with the admin token (spec/protocol.md, Joining with the admin
 * token): asks, then waits for a member to answer with its code, which this device then scans as if
 * it had read it from a screen.
 */
suspend fun askToJoin(
    server: ServerStore,
    adminToken: String,
    name: String,
): Code.Member {
    val id = SyncBytes.toHex(SyncCrypto.randomBytes(16))
    server.requestJoin(adminToken, id, name.take(NAME_LIMIT))
    var waited = 0L
    while (true) {
        server.joinAnswer(adminToken, id)?.let { return readCode(it) as? Code.Member ?: throw LinkedException.Unexpected() }
        if (waited >= SLOT_LIFETIME_MS) throw LinkedException.TimedOut()
        delay(POLL_MS)
        waited += POLL_MS
    }
}
