package io.github.aedev.flow.sync2.crypto

import java.security.MessageDigest

private fun blobAad(
    group: ByteArray,
    epoch: Int,
    did: ByteArray,
): ByteArray = tag("blob") + group + u32(epoch) + did

/** Seals a snapshot blob; its name is the SHA-256 of the result. */
fun sealBlob(
    snapshotKey: ByteArray,
    group: ByteArray,
    epoch: Int,
    did: ByteArray,
    payload: ByteArray,
): ByteArray = seal(snapshotKey, blobAad(group, epoch, did), payload)

fun openBlob(
    snapshotKey: ByteArray,
    group: ByteArray,
    epoch: Int,
    did: ByteArray,
    blob: ByteArray,
    expectedHash: ByteArray,
): ByteArray {
    if (!MessageDigest.isEqual(sha256(blob), expectedHash)) reject(ProtocolError.BLOB_HASH)
    return open(snapshotKey, blobAad(group, epoch, did), blob)
}
