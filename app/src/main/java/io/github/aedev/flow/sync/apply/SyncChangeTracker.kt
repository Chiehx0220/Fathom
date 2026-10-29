package io.github.aedev.flow.sync.apply

import io.github.aedev.flow.sync.apply.SyncChangeLedgerStore.Companion.GROUPS
import io.github.aedev.flow.sync.apply.SyncChangeLedgerStore.Companion.GROUP_MEMBERS
import io.github.aedev.flow.sync.apply.SyncChangeLedgerStore.Companion.LIKES
import io.github.aedev.flow.sync.apply.SyncChangeLedgerStore.Companion.NOTES
import io.github.aedev.flow.sync.apply.SyncChangeLedgerStore.Companion.PLAYLISTS
import io.github.aedev.flow.sync.apply.SyncChangeLedgerStore.Companion.PLAYLIST_ITEMS
import io.github.aedev.flow.sync.apply.SyncChangeLedgerStore.Companion.SETTINGS
import io.github.aedev.flow.sync.apply.SyncChangeLedgerStore.Companion.SETTINGS_RELAY
import io.github.aedev.flow.sync.apply.SyncChangeLedgerStore.Companion.WATCH_HISTORY
import io.github.aedev.flow.sync.canonical.CanonicalGroupMember
import io.github.aedev.flow.sync.canonical.CanonicalLike
import io.github.aedev.flow.sync.canonical.CanonicalNote
import io.github.aedev.flow.sync.canonical.CanonicalPlaylist
import io.github.aedev.flow.sync.canonical.CanonicalPlaylistItem
import io.github.aedev.flow.sync.canonical.CanonicalSetting
import io.github.aedev.flow.sync.canonical.CanonicalSubscriptionGroup
import io.github.aedev.flow.sync.canonical.CanonicalWatchHistory
import io.github.aedev.flow.sync.identity.DeviceIdentity
import io.github.aedev.flow.sync.identity.Hlc
import io.github.aedev.flow.sync.mapping.PlaylistMapper
import io.github.aedev.flow.sync.mapping.SettingsMapper
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads each ledgered collection for sync: [SyncDataAccess]'s records, stamped with when they last
 * changed on this device, plus a tombstone for everything deleted here or relayed from a peer since.
 * After a merge has been written, the matching `settle*` records the outcome so the next sync diffs
 * against it. Watch history, likes and notes already carry their own event times, so only their
 * deletions are tracked; groups, playlists and settings also get edit stamps.
 */
@Singleton
class SyncChangeTracker
    @Inject
    constructor(
        private val data: SyncDataAccess,
        private val store: SyncChangeLedgerStore,
        private val identity: DeviceIdentity,
    ) {
        private val json =
            Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }

        // --- watch history ---

        suspend fun watchHistory(node: String): List<CanonicalWatchHistory> {
            val live = data.readWatchHistory(node)
            val at = now()
            val ledger = store.update(WATCH_HISTORY) { it.observe(live.associate { r -> r.videoId to PRESENT }, at) }
            return live +
                ledger.tombstones().map { (videoId, e) ->
                    peerRecord(e, CanonicalWatchHistory.serializer())
                        ?: CanonicalWatchHistory(videoId = videoId, hlc = stamp(e.atMs, node), deleted = true)
                }
        }

        suspend fun settleWatchHistory(merged: List<CanonicalWatchHistory>) =
            settle(
                WATCH_HISTORY,
                merged.filter { !it.deleted }.associate { it.videoId to PRESENT },
                merged.filter { it.deleted }.associate { it.videoId to peer(it.hlc, it, CanonicalWatchHistory.serializer()) },
            )

        // --- likes ---

        suspend fun likes(node: String): List<CanonicalLike> {
            val live = data.readLikes(node)
            val at = now()
            val ledger = store.update(LIKES) { it.observe(live.associate { r -> likeKey(r) to PRESENT }, at) }
            return live +
                ledger.tombstones().mapNotNull { (key, e) ->
                    peerRecord(e, CanonicalLike.serializer()) ?: likeTombstone(key, e.atMs, node)
                }
        }

        suspend fun settleLikes(merged: List<CanonicalLike>) =
            settle(
                LIKES,
                merged.filter { it.state == CanonicalLike.STATE_LIKED }.associate { likeKey(it) to PRESENT },
                merged
                    .filter { it.state == CanonicalLike.STATE_NONE }
                    .associate { likeKey(it) to peer(it.hlc, it, CanonicalLike.serializer()) },
            )

        // --- notes ---

        suspend fun notes(): List<CanonicalNote> {
            val live = data.readNotes()
            val at = now()
            val ledger = store.update(NOTES) { it.observe(live.associate { n -> n.id to PRESENT }, at) }
            return live +
                ledger.tombstones().map { (id, e) ->
                    peerRecord(e, CanonicalNote.serializer()) ?: CanonicalNote(id = id, updatedAt = e.atMs, deleted = true)
                }
        }

        suspend fun settleNotes(merged: List<CanonicalNote>) =
            settle(
                NOTES,
                merged.filter { !it.deleted && it.text.isNotBlank() }.associate { it.id to PRESENT },
                merged
                    .filter { it.deleted }
                    .associate { it.id to PeerTombstone(it.updatedAt, json.encodeToString(CanonicalNote.serializer(), it)) },
            )

        // --- subscription groups ---

        suspend fun groups(node: String): List<CanonicalSubscriptionGroup> {
            val live = data.readSubscriptions(hlc = "")
            val at = now()
            val ledger = store.update(GROUPS) { it.observe(live.associate { g -> g.name to groupHash(g) }, at) }
            val members = store.update(GROUP_MEMBERS) { it.observe(memberKeys(live), at) }
            return live.map { g ->
                val present = g.channelIds.toSet()
                g.copy(
                    hlc = stamp(ledger.stampOf(g.name), node),
                    members =
                        (
                            present.map { CanonicalGroupMember(it, stamp(members.stampOf(memberKey(g.name, it)), node)) } +
                                tombstonesUnder(members, memberKey(g.name, ""), present, CanonicalGroupMember.serializer()) { id, atMs ->
                                    CanonicalGroupMember(id, stamp(atMs, node), deleted = true)
                                }
                        ).sortedBy { it.channelId },
                )
            } +
                ledger.tombstones().map { (name, e) ->
                    peerRecord(e, CanonicalSubscriptionGroup.serializer())
                        ?: CanonicalSubscriptionGroup(name = name, hlc = stamp(e.atMs, node), deleted = true)
                }
        }

        suspend fun settleGroups(merged: List<CanonicalSubscriptionGroup>) {
            val written = data.readSubscriptions(hlc = "")
            val (deleted, live) = merged.partition { it.deleted }
            settle(
                GROUPS,
                written.associate { it.name to groupHash(it) },
                deleted.associate { it.name to peer(it.hlc, it, CanonicalSubscriptionGroup.serializer()) },
                stamps = live.associate { it.name to physical(it.hlc) },
            )
            val memberStates = live.flatMap { g -> g.members.map { memberKey(g.name, it.channelId) to it } }
            settle(
                GROUP_MEMBERS,
                memberKeys(written),
                memberStates.filter { it.second.deleted }.associate { (key, m) ->
                    key to peer(m.hlc, m, CanonicalGroupMember.serializer())
                },
                stamps = memberStates.filter { !it.second.deleted }.associate { (key, m) -> key to physical(m.hlc) },
            )
        }

        // --- playlists ---

        suspend fun playlists(node: String): List<CanonicalPlaylist> {
            val live = data.readPlaylists(hlc = "")
            val at = now()
            val playlists =
                store.update(PLAYLISTS) {
                    it.observe(live.associate { p -> p.syncId to playlistHash(p) }, at, templates(live))
                }
            val items = store.update(PLAYLIST_ITEMS) { it.observe(itemHashes(live), at) }
            val liveIds = live.mapTo(HashSet()) { it.syncId }
            val stamped =
                live.map { p ->
                    val present = p.items.mapTo(HashSet()) { it.videoId }
                    p.copy(
                        updatedHlc = stamp(playlists.stampOf(p.syncId), node),
                        items =
                            p.items.map { it.copy(hlc = stamp(items.stampOf(itemKey(p.syncId, it.videoId)), node)) } +
                                tombstonesUnder(
                                    items,
                                    itemKey(p.syncId, ""),
                                    present,
                                    CanonicalPlaylistItem.serializer(),
                                ) { videoId, atMs ->
                                    CanonicalPlaylistItem(videoId = videoId, deleted = true, hlc = stamp(atMs, node))
                                },
                    )
                }
            val deleted =
                playlists
                    .tombstones()
                    .filterKeys { it !in liveIds && it !in PROTECTED_PLAYLISTS }
                    .map { (syncId, e) -> peerRecord(e, CanonicalPlaylist.serializer()) ?: playlistTombstone(syncId, e, node) }
            return stamped + deleted
        }

        suspend fun settlePlaylists(merged: List<CanonicalPlaylist>) {
            val written = data.readPlaylists(hlc = "")
            val (deleted, live) = merged.partition { it.deleted }
            settle(
                PLAYLISTS,
                written.associate { it.syncId to playlistHash(it) },
                deleted.associate { it.syncId to peer(it.updatedHlc, it.copy(items = emptyList()), CanonicalPlaylist.serializer()) },
                stamps = live.associate { it.syncId to physical(it.updatedHlc) },
                templates = templates(written),
            )
            settle(
                PLAYLIST_ITEMS,
                itemHashes(written),
                live
                    .flatMap { p -> p.items.filter { it.deleted }.map { p.syncId to it } }
                    .associate { (syncId, item) ->
                        itemKey(syncId, item.videoId) to peer(item.hlc, item, CanonicalPlaylistItem.serializer())
                    },
                stamps =
                    live
                        .flatMap { p -> p.items.filter { !it.deleted }.map { itemKey(p.syncId, it.videoId) to physical(it.hlc) } }
                        .toMap(),
            )
        }

        /** Tombstones of the entries under [prefix] that aren't [present]: a peer's record, or one stamped here. */
        private fun <T> tombstonesUnder(
            ledger: CollectionLedger,
            prefix: String,
            present: Set<String>,
            serializer: KSerializer<T>,
            make: (id: String, atMs: Long) -> T,
        ): List<T> =
            ledger
                .tombstones()
                .filterKeys { it.startsWith(prefix) && it.removePrefix(prefix) !in present }
                .map { (key, e) -> peerRecord(e, serializer) ?: make(key.removePrefix(prefix), e.atMs) }

        private fun memberKeys(groups: List<CanonicalSubscriptionGroup>): Map<String, Int> =
            groups.flatMap { g -> g.channelIds.map { memberKey(g.name, it) to PRESENT } }.toMap()

        private fun playlistTombstone(
            syncId: String,
            e: LedgerEntry,
            node: String,
        ): CanonicalPlaylist {
            val template = e.record?.let { runCatching { json.decodeFromString(CanonicalPlaylist.serializer(), it) }.getOrNull() }
            return (template ?: CanonicalPlaylist(syncId = syncId)).copy(
                syncId = syncId,
                updatedHlc = stamp(e.atMs, node),
                deleted = true,
                items = emptyList(),
            )
        }

        private fun templates(playlists: List<CanonicalPlaylist>): Map<String, String> =
            playlists.associate {
                it.syncId to json.encodeToString(CanonicalPlaylist.serializer(), it.copy(items = emptyList(), updatedHlc = ""))
            }

        private fun itemHashes(playlists: List<CanonicalPlaylist>): Map<String, Int> =
            playlists
                .flatMap { p -> p.items.map { itemKey(p.syncId, it.videoId) to it.position.hashCode() } }
                .toMap()

        // --- settings ---

        suspend fun settings(node: String): List<CanonicalSetting> {
            val live = data.readSettings(hlc = "")
            val at = now()
            val ledger = store.update(SETTINGS) { it.observe(live.associate { s -> s.key to s.value.toString().hashCode() }, at) }
            val liveKeys = live.mapTo(HashSet()) { it.key }
            val relayed =
                store
                    .load(SETTINGS_RELAY)
                    .entries
                    .filterKeys { it !in liveKeys }
                    .values
                    .mapNotNull { e -> e.record?.let { decodeOrNull(it, CanonicalSetting.serializer()) } }
            return live.map { it.copy(hlc = stamp(ledger.stampOf(it.key), node)) } + relayed
        }

        /** Settings this build has no slot for (the desktop's own) are kept and passed on unchanged. */
        suspend fun settleSettings(merged: List<CanonicalSetting>) {
            settle(
                SETTINGS,
                data.readSettings(hlc = "").associate { it.key to it.value.toString().hashCode() },
                emptyMap(),
                stamps = merged.associate { it.key to physical(it.hlc) },
            )
            val known = SettingsMapper.WHITELIST.mapTo(HashSet()) { it.canonical }
            store.update(SETTINGS_RELAY) {
                CollectionLedger(
                    initialized = true,
                    entries =
                        merged
                            .filter { it.key !in known }
                            .associate { it.key to LedgerEntry(record = json.encodeToString(CanonicalSetting.serializer(), it)) },
                )
            }
        }

        // --- shared ---

        private suspend fun settle(
            collection: String,
            current: Map<String, Int>,
            peerTombstones: Map<String, PeerTombstone>,
            stamps: Map<String, Long> = emptyMap(),
            templates: Map<String, String> = emptyMap(),
        ) {
            store.update(collection) { it.settle(current, stamps, peerTombstones, templates) }
            (stamps.values + peerTombstones.values.map { it.atMs }).maxOrNull()?.let { identity.observe(it) }
        }

        private fun <T> peer(
            hlc: String,
            record: T,
            serializer: KSerializer<T>,
        ) = PeerTombstone(physical(hlc), json.encodeToString(serializer, record))

        private fun <T> peerRecord(
            e: LedgerEntry,
            serializer: KSerializer<T>,
        ): T? = e.record?.takeIf { e.fromPeer }?.let { decodeOrNull(it, serializer) }

        private fun <T> decodeOrNull(
            raw: String,
            serializer: KSerializer<T>,
        ): T? = runCatching { json.decodeFromString(serializer, raw) }.getOrNull()

        private fun likeTombstone(
            key: String,
            atMs: Long,
            node: String,
        ): CanonicalLike? {
            val (kind, id) = key.split('|', limit = 2).takeIf { it.size == 2 } ?: return null
            return CanonicalLike(kind = kind, id = id, state = CanonicalLike.STATE_NONE, updatedAtMs = atMs, hlc = stamp(atMs, node))
        }

        private suspend fun now() = identity.editTime()

        private companion object {
            const val PRESENT = 1
            val PROTECTED_PLAYLISTS = setOf(CanonicalPlaylist.RESERVED_WATCH_LATER, PlaylistMapper.SAVED_SHORTS_ID)

            fun stamp(
                atMs: Long,
                node: String,
            ) = Hlc(atMs, 0, node).encode()

            fun physical(hlc: String) = Hlc.decode(hlc).physicalMs

            fun likeKey(like: CanonicalLike) = "${like.kind}|${like.id}"

            fun itemKey(
                syncId: String,
                videoId: String,
            ) = "$syncId|$videoId"

            // Group names are typed by the user, so the separator is one a name can't hold.
            fun memberKey(
                group: String,
                channelId: String,
            ) = "$group\u0000$channelId"

            fun groupHash(g: CanonicalSubscriptionGroup) = "${g.channelIds.sorted()}|${g.sortOrder}".hashCode()

            fun playlistHash(p: CanonicalPlaylist) = "${p.title}|${p.description}|${p.isMusic}|${p.isUserCreated}|${p.youtubeId}".hashCode()
        }
    }
