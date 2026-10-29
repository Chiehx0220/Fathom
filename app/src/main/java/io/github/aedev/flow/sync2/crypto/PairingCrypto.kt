package io.github.aedev.flow.sync2.crypto

import java.nio.ByteBuffer

class PairingKeys(
    /** Seals messages from the device showing the code. */
    val showerKey: ByteArray,
    /** Seals messages from the scanner. */
    val scannerKey: ByteArray,
    val sas: String,
)

fun pairingKeys(
    ownSecret: ByteArray,
    peerPublic: ByteArray,
    token: ByteArray,
    showerPublic: ByteArray,
    scannerPublic: ByteArray,
): PairingKeys {
    val shared = agree(ownSecret, peerPublic)
    val transcript = showerPublic + scannerPublic
    val sas = (ByteBuffer.wrap(derive(shared, token, "sas", transcript, 4)).int and 0x7fff_ffff) % 1_000_000
    return PairingKeys(
        showerKey = derive(shared, token, "pair a2b", transcript, 32),
        scannerKey = derive(shared, token, "pair b2a", transcript, 32),
        sas = sas.toString().padStart(6, '0'),
    ).also { shared.fill(0) }
}

fun pairingAad(
    token: ByteArray,
    seq: Long,
): ByteArray = tag("pair") + token + u64(seq)

fun pairingSlot(token: ByteArray): ByteArray = labeledId("pair", token, 16)
