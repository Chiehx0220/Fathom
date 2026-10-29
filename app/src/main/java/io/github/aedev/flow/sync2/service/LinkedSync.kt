package io.github.aedev.flow.sync2.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.profile.ActiveProfile
import io.github.aedev.flow.data.profile.SyncProfile
import io.github.aedev.flow.notification.NotificationHelper
import io.github.aedev.flow.sync.apply.PeerInfo
import io.github.aedev.flow.sync.apply.SyncApplier
import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync.identity.DeviceIdentity
import io.github.aedev.flow.sync.protocol.CollectionWire
import io.github.aedev.flow.sync.protocol.SyncCollection
import io.github.aedev.flow.sync2.changes.applyBatches
import io.github.aedev.flow.sync2.crypto.MemberSecrets
import io.github.aedev.flow.sync2.data.GroupRecord
import io.github.aedev.flow.sync2.data.LAN_STORE
import io.github.aedev.flow.sync2.data.LinkedDatabase
import io.github.aedev.flow.sync2.device.DeviceKeys
import io.github.aedev.flow.sync2.engine.Session
import io.github.aedev.flow.sync2.engine.collectGarbage
import io.github.aedev.flow.sync2.engine.isDue
import io.github.aedev.flow.sync2.engine.writeSnapshot
import io.github.aedev.flow.sync2.linking.deviceNames
import io.github.aedev.flow.sync2.store.GroupStore
import io.github.aedev.flow.sync2.store.LocalStore
import io.github.aedev.flow.sync2.store.ServerStore
import io.github.aedev.flow.sync2.store.StoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** When the last sync ran, and why it failed if it did. */
data class LastSync(
    val atMs: Long,
    val error: LinkedError?,
)

/** What one sync applied from one device. */
data class Received(
    val from: String,
    val changes: Int,
)

/**
 * One sync of this device's group, for the worker and for Sync now. Like FLOW-SYNC/1 it runs only
 * in the Default profile, and one at a time.
 */
@Singleton
class LinkedSync
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val db: LinkedDatabase,
        private val keys: DeviceKeys,
        private val applier: SyncApplier,
        private val identity: DeviceIdentity,
        private val local: LocalStore,
        private val indicator: SyncIndicator,
    ) {
        private val running = Mutex()
        private val last = MutableStateFlow<LastSync?>(null)
        private val active = MutableStateFlow(false)
        private val arrivals = MutableSharedFlow<Received>(extraBufferCapacity = 16)

        val lastSync: StateFlow<LastSync?> = last.asStateFlow()

        val syncing: StateFlow<Boolean> = active.asStateFlow()

        /** Each device a sync the user asked for applied changes from. */
        val received: SharedFlow<Received> = arrivals.asSharedFlow()

        /** The group's Flow server, or null for a group that lives on its devices alone. */
        fun server(
            record: GroupRecord,
            device: MemberSecrets,
        ): ServerStore? =
            if (record.storeUrl ==
                LAN_STORE
            ) {
                null
            } else {
                ServerStore.connect(record.storeUrl, device, record.instance, record.groupId)
            }

        fun groupStore(
            record: GroupRecord,
            device: MemberSecrets,
        ): GroupStore = GroupStore(local, server(record, device))

        /** A session on what this device holds, after copying the server's roster and keys. */
        suspend fun open(
            record: GroupRecord,
            device: MemberSecrets,
        ): Session {
            val store = groupStore(record, device)
            store.download(SyncBytes.toHex(device.did), record.serverCursor, objects = false)
            return Session.open(db, store, device)
        }

        /** [announce]: say what arrived, for a sync the user asked for; the others stay silent. */
        suspend fun syncNow(announce: Boolean = false): LastSync =
            running.withLock {
                active.value = true
                // A cancelled sync is not a failed one: it stops, and the next run picks up where it left off.
                val error =
                    try {
                        withContext(Dispatchers.Default) { run(announce) }
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.linkedError()
                    } finally {
                        active.value = false
                        indicator.hide()
                    }
                error?.let { Log.w(TAG, "Linked-devices sync failed: $it") }
                LastSync(System.currentTimeMillis(), error).also { last.value = it }
            }

        private suspend fun export(collections: List<String> = SyncCollection.ANDROID_SYNCABLE): Map<String, CollectionWire> =
            applier.exportPayload(collections, identity.hlcNode(), identity.newClock().now().encode())

        private suspend fun run(announce: Boolean) {
            if (!SyncProfile.syncsHere(context)) return
            val record = db.groups().group() ?: return
            val device = keys.load()
            val me = SyncBytes.toHex(device.did)
            val store = groupStore(record, device)
            // Without the server, what LAN peers brought is still merged; the server's part waits for the next run.
            val offline =
                try {
                    db.groups().setServerCursor(store.download(me, record.serverCursor, objects = true))
                    null
                } catch (e: StoreException.Network) {
                    e
                }
            val session = Session.open(db, if (offline == null) store else GroupStore(local, null), device)
            if (!session.adoptLink(ActiveProfile.get(context).id)) return
            val link = session.record.link
            val pushed = session.push(export())

            val pulled = session.pull()
            if (pushed > 0 || pulled.batches.isNotEmpty()) indicator.show()
            val names = if (pulled.batches.isEmpty()) emptyMap() else deviceNames(session)
            val hlc = identity.newClock().now().encode()
            val counts = mutableMapOf<String, Int>()
            for ((sender, batches) in pulled.batches) {
                val meta = names[sender]
                applyBatches(db.sent(), batches, System.currentTimeMillis(), ::export) { received ->
                    applier.applyPayload(PeerInfo(sender, meta?.name.orEmpty(), meta?.platform.orEmpty()), received, hlc, onceEach = false)
                    counts.merge(sender, received.values.sumOf { it.lines.size }, Int::plus)
                }
            }
            session.finish(pulled)
            if (announce) {
                counts.filterValues { it > 0 }.forEach { (sender, changes) ->
                    arrivals.tryEmit(Received(names[sender]?.name.orEmpty(), changes))
                }
            }

            val now = System.currentTimeMillis()
            db.snapshots().noteMerged(link, pulled.batches.values.sumOf { batches -> batches.sumOf { it.size.toLong() } }, now)
            if (db.snapshots().isDue(link, now)) session.writeSnapshot(export())
            if (offline != null) throw offline
            session.collectGarbage()
            db.groups().setUploaded(store.upload(me, db.groups().group()?.uploaded ?: 0))
            // The sync is done; a failed look for devices asking to join only waits for the next one.
            try {
                server(record, device)?.joinRequests()?.firstOrNull()?.let { NotificationHelper.showJoinRequest(context, it.name) }
            } catch (_: StoreException) {
            }
        }

        private companion object {
            const val TAG = "LinkedSync"
        }
    }
