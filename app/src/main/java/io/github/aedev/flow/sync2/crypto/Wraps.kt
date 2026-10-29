package io.github.aedev.flow.sync2.crypto

import org.conscrypt.Conscrypt
import org.conscrypt.HpkeContextRecipient
import org.conscrypt.HpkeContextSender
import org.conscrypt.XdhKeySpec
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Provider
import java.security.PublicKey

/** RFC 9180 base mode: X25519, HKDF-SHA256, AES-256-GCM. */
private const val SUITE = "DHKEM_X25519_HKDF_SHA256/HKDF_SHA256/AES_256_GCM"
const val WRAP_SIZE = 80
private const val ENC_SIZE = 32

// The bundled Conscrypt, not the platform's: HPKE isn't in the platform before API 34.
private val conscrypt: Provider by lazy { Conscrypt.newProvider() }

private fun info(
    group: ByteArray,
    epoch: Int,
    did: ByteArray,
): ByteArray = tag("wrap") + group + u32(epoch) + did

private fun wrap(
    kemPublic: ByteArray,
    epochKey: ByteArray,
    init: HpkeContextSender.(PublicKey) -> Unit,
): ByteArray =
    try {
        val recipient = KeyFactory.getInstance("XDH", conscrypt).generatePublic(XdhKeySpec(kemPublic))
        val sender = HpkeContextSender.getInstance(SUITE, conscrypt).apply { init(recipient) }
        val sealed = sender.seal(epochKey, ByteArray(0))
        sender.encapsulated + sealed
    } catch (_: GeneralSecurityException) {
        reject(ProtocolError.X25519)
    }

fun wrapEpochKey(
    kemPublic: ByteArray,
    group: ByteArray,
    epoch: Int,
    did: ByteArray,
    epochKey: ByteArray,
): ByteArray = wrap(kemPublic, epochKey) { init(it, info(group, epoch, did)) }

/** Uses [ephemeralSecret] as the sender's ephemeral X25519 key, so tests can reproduce a wrap. */
fun wrapEpochKeyWithEphemeral(
    kemPublic: ByteArray,
    group: ByteArray,
    epoch: Int,
    did: ByteArray,
    epochKey: ByteArray,
    ephemeralSecret: ByteArray,
): ByteArray = wrap(kemPublic, epochKey) { initForTesting(it, info(group, epoch, did), ephemeralSecret) }

fun unwrapEpochKey(
    kemSecret: ByteArray,
    group: ByteArray,
    epoch: Int,
    did: ByteArray,
    wrap: ByteArray,
): ByteArray {
    if (wrap.size != WRAP_SIZE) reject(ProtocolError.LENGTH)
    return try {
        val secret = KeyFactory.getInstance("XDH", conscrypt).generatePrivate(XdhKeySpec(kemSecret))
        HpkeContextRecipient
            .getInstance(SUITE, conscrypt)
            .apply { init(wrap.copyOfRange(0, ENC_SIZE), secret, info(group, epoch, did)) }
            .open(wrap.copyOfRange(ENC_SIZE, WRAP_SIZE), ByteArray(0))
    } catch (_: GeneralSecurityException) {
        reject(ProtocolError.DECRYPT)
    }
}
