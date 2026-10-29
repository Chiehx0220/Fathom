package io.github.aedev.flow.sync2.crypto

import io.github.aedev.flow.sync.crypto.SyncCrypto
import java.nio.ByteBuffer
import java.security.MessageDigest

private val PREFIX = "FLOW-SYNC/2 ".toByteArray(Charsets.US_ASCII)
private val SEPARATOR = byteArrayOf(0)

/** The protocol prefix for labels used as AAD or signature context. */
fun tag(label: String): ByteArray = PREFIX + label.toByteArray(Charsets.US_ASCII)

/** `HKDF-Expand(HKDF-Extract(salt, ikm), "FLOW-SYNC/2 " ‖ label ‖ 0x00 ‖ context, length)`. */
fun derive(
    ikm: ByteArray,
    salt: ByteArray,
    label: String,
    context: ByteArray,
    length: Int,
): ByteArray {
    // JCE refuses an empty HMAC key; RFC 5869 reads an empty salt as zeros, which HMAC pads to anyway.
    val extractSalt = if (salt.isEmpty()) ByteArray(32) else salt
    return SyncCrypto.hkdf(ikm, extractSalt, tag(label) + SEPARATOR + context, length)
}

fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

fun labeledHash(
    label: String,
    data: ByteArray,
): ByteArray = sha256(tag(label) + SEPARATOR + data)

/** The first [length] bytes of a labelled hash, used for ids. */
fun labeledId(
    label: String,
    data: ByteArray,
    length: Int,
): ByteArray = labeledHash(label, data).copyOf(length)

fun u32(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

fun u64(value: Long): ByteArray = ByteBuffer.allocate(8).putLong(value).array()
