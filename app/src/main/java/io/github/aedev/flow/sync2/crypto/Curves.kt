package io.github.aedev.flow.sync2.crypto

import io.github.aedev.flow.sync.crypto.SyncBytes
import org.bouncycastle.math.ec.rfc7748.X25519
import org.bouncycastle.math.ec.rfc8032.Ed25519
import java.math.BigInteger

private const val KEY_SIZE = 32
private const val SIGNATURE_SIZE = 64

/** 2^255 - 19; an encoded y at or above it is non-canonical. */
private val FIELD_PRIME = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))

/** The eight canonical encodings of small-order points (spec/crypto.md, Ed25519 checks). */
private val SMALL_ORDER_KEYS =
    listOf(
        "0100000000000000000000000000000000000000000000000000000000000000",
        "ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f",
        "0000000000000000000000000000000000000000000000000000000000000000",
        "0000000000000000000000000000000000000000000000000000000000000080",
        "26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc05",
        "26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc85",
        "c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac037a",
        "c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac03fa",
    )

private fun canonicalY(key: ByteArray): Boolean {
    val y = key.reversedArray().also { it[0] = (it[0].toInt() and 0x7f).toByte() }
    return BigInteger(1, y) < FIELD_PRIME
}

/** Rejects non-canonical encodings, small-order keys, and bytes that aren't a curve point. */
fun isValidSigningKey(publicKey: ByteArray): Boolean =
    publicKey.size == KEY_SIZE &&
        canonicalY(publicKey) &&
        SyncBytes.toHex(publicKey) !in SMALL_ORDER_KEYS &&
        Ed25519.validatePublicKeyPartial(publicKey, 0)

fun signingPublicKey(seed: ByteArray): ByteArray = ByteArray(KEY_SIZE).also { Ed25519.generatePublicKey(seed, 0, it, 0) }

fun sign(
    seed: ByteArray,
    message: ByteArray,
): ByteArray = ByteArray(SIGNATURE_SIZE).also { Ed25519.sign(seed, 0, message, 0, message.size, it, 0) }

fun verify(
    publicKey: ByteArray,
    message: ByteArray,
    signature: ByteArray,
): Boolean =
    signature.size == SIGNATURE_SIZE &&
        isValidSigningKey(publicKey) &&
        Ed25519.verify(signature, 0, publicKey, 0, message, 0, message.size)

fun agreementPublicKey(secret: ByteArray): ByteArray = ByteArray(KEY_SIZE).also { X25519.scalarMultBase(secret, 0, it, 0) }

/** X25519, refusing the all-zero output of a small-order peer key. */
fun agree(
    secret: ByteArray,
    publicKey: ByteArray,
): ByteArray {
    if (publicKey.size != KEY_SIZE) reject(ProtocolError.X25519)
    val shared = ByteArray(KEY_SIZE)
    if (!X25519.calculateAgreement(secret, 0, publicKey, 0, shared, 0)) reject(ProtocolError.X25519)
    return shared
}
