package io.github.aedev.flow.sync2.linking

import io.github.aedev.flow.sync.crypto.SyncBytes
import io.github.aedev.flow.sync.crypto.SyncCrypto
import io.github.aedev.flow.sync2.crypto.MemberSecrets
import io.github.aedev.flow.sync2.crypto.recoveryKeys
import io.github.aedev.flow.sync2.crypto.recoveryWords
import io.github.aedev.flow.sync2.crypto.wrapEpochKey
import io.github.aedev.flow.sync2.data.GroupRecord
import io.github.aedev.flow.sync2.data.LAN_STORE
import io.github.aedev.flow.sync2.data.LinkedDatabase
import io.github.aedev.flow.sync2.data.addEntries
import io.github.aedev.flow.sync2.engine.LinkedException
import io.github.aedev.flow.sync2.roster.Draft
import io.github.aedev.flow.sync2.roster.Entry
import io.github.aedev.flow.sync2.roster.Op
import io.github.aedev.flow.sync2.roster.Role
import io.github.aedev.flow.sync2.roster.buildEntry
import io.github.aedev.flow.sync2.roster.entryHash
import io.github.aedev.flow.sync2.roster.epochDraft
import io.github.aedev.flow.sync2.roster.memberSubject
import io.github.aedev.flow.sync2.roster.sealDeviceMeta
import io.github.aedev.flow.sync2.store.LocalStore
import io.github.aedev.flow.sync2.store.ServerStore

/** The genesis of a new group with this device and a new recovery key, and the recovery words to show once. */
private class Genesis(
    device: MemberSecrets,
    meta: DeviceMeta,
) {
    val group: ByteArray = SyncCrypto.randomBytes(16)
    val entry: Entry
    val wraps: Map<String, String>
    val words: String

    init {
        val epochKey = SyncCrypto.randomBytes(32)
        val recoverySecret = SyncCrypto.randomBytes(32)
        val recovery = recoveryKeys(recoverySecret)
        val members = listOf(device, recovery)
        val sealedMeta = sealDeviceMeta(epochKey, group, 0, 0, device.did, metaJson(meta))
        val draft =
            Draft(
                op = Op.GENESIS,
                signer = SyncBytes.toHex(device.did),
                add =
                    listOf(
                        memberSubject(device.sigPublic, device.kemPublic, Role.DEVICE),
                        memberSubject(recovery.sigPublic, recovery.kemPublic, Role.RECOVERY),
                    ),
                epoch = epochDraft(epochKey, group, 0, members.map { SyncBytes.toHex(it.did) }),
                meta = mapOf(SyncBytes.toHex(device.did) to SyncBytes.b64urlEncode(sealedMeta)),
            )
        entry = buildEntry(group, null, draft, members)
        wraps =
            members.associate { SyncBytes.toHex(it.did) to SyncBytes.b64urlEncode(wrapEpochKey(it.kemPublic, group, 0, it.did, epochKey)) }
        words = recoveryWords(recoverySecret)
        recovery.wipe()
        recoverySecret.fill(0)
        epochKey.fill(0)
    }
}

/**
 * Creates the group, claims the server with the operator's admin token, and returns the recovery
 * words to show once.
 */
suspend fun createGroup(
    db: LinkedDatabase,
    storeUrl: String,
    adminToken: String,
    device: MemberSecrets,
    meta: DeviceMeta,
    link: String,
): String {
    val genesis = Genesis(device, meta)
    val store = ServerStore.connect(storeUrl, device, null, SyncBytes.toHex(genesis.group))
    store.claim(adminToken, genesis.entry, genesis.wraps)
    val roster = store.rosterAfter(-1)
    if (roster.map(::entryHash) != listOf(entryHash(genesis.entry))) throw LinkedException.Unexpected()
    db.groups().addEntries(roster)
    db.groups().saveGroup(GroupRecord(SyncBytes.toHex(genesis.group), storeUrl, store.identity().first, link))
    return genesis.words
}

/** Creates a group that lives on its devices alone (spec/live.md, LAN groups): nothing is claimed. */
suspend fun createLanGroup(
    db: LinkedDatabase,
    local: LocalStore,
    device: MemberSecrets,
    meta: DeviceMeta,
    link: String,
): String {
    val genesis = Genesis(device, meta)
    local.hold(genesis.entry, 0, genesis.wraps.mapValues { (_, wrap) -> SyncBytes.b64urlDecode(wrap) }, null)
    db.groups().addEntries(listOf(genesis.entry))
    db.groups().saveGroup(GroupRecord(SyncBytes.toHex(genesis.group), LAN_STORE, null, link))
    return genesis.words
}
