package io.github.aedev.flow.sync2.crypto

import io.github.aedev.flow.sync.crypto.SyncCrypto
import java.security.GeneralSecurityException
import java.security.MessageDigest

private const val SALT = 32
private const val COMMIT = 32
private const val TAG = 16
const val BOX_OVERHEAD = SALT + COMMIT + TAG

/** AES-256-GCM under a key derived per box from a random salt, plus a key commitment. */
private class BoxKeys(
    key: ByteArray,
    salt: ByteArray,
) {
    private val okm = derive(key, salt, "box", ByteArray(0), 76)
    val aesKey: ByteArray = okm.copyOfRange(0, 32)
    val nonce: ByteArray = okm.copyOfRange(32, 44)
    val commit: ByteArray = okm.copyOfRange(44, 76)
}

fun seal(
    key: ByteArray,
    aad: ByteArray,
    plaintext: ByteArray,
): ByteArray = sealWithSalt(key, aad, plaintext, SyncCrypto.randomBytes(SALT))

/** Only for reproducing test vectors; production boxes take a fresh random salt. */
fun sealWithSalt(
    key: ByteArray,
    aad: ByteArray,
    plaintext: ByteArray,
    salt: ByteArray,
): ByteArray {
    val keys = BoxKeys(key, salt)
    return salt + keys.commit + SyncCrypto.seal(keys.aesKey, keys.nonce, plaintext, aad)
}

fun open(
    key: ByteArray,
    aad: ByteArray,
    sealed: ByteArray,
): ByteArray {
    if (sealed.size < BOX_OVERHEAD) reject(ProtocolError.LENGTH)
    val keys = BoxKeys(key, sealed.copyOfRange(0, SALT))
    if (!MessageDigest.isEqual(keys.commit, sealed.copyOfRange(SALT, SALT + COMMIT))) reject(ProtocolError.COMMITMENT)
    return try {
        SyncCrypto.open(keys.aesKey, keys.nonce, sealed.copyOfRange(SALT + COMMIT, sealed.size), aad)
    } catch (_: GeneralSecurityException) {
        reject(ProtocolError.DECRYPT)
    }
}
