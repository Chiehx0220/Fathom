package io.github.aedev.flow.sync2.crypto

/** The mDNS TXT tag for one hour; members check the previous, current and next hour. */
fun lanTag(
    lanKey: ByteArray,
    unixSeconds: Long,
): ByteArray = derive(lanKey, ByteArray(0), "lan tag", u64(unixSeconds / 3600), 8)

fun lanHelloAad(message: Int): ByteArray = tag("lan hello") + byteArrayOf(message.toByte())

enum class LanRole(
    val label: String,
) {
    SERVER("lan server"),
    CLIENT("lan client"),
}

fun lanTranscript(
    role: LanRole,
    clientNonce: ByteArray,
    serverNonce: ByteArray,
    client: ByteArray,
    server: ByteArray,
): ByteArray = tag(role.label) + clientNonce + serverNonce + client + server

class LanSessionKeys(
    val clientToServer: ByteArray,
    val serverToClient: ByteArray,
)

fun lanSessionKeys(
    lanKey: ByteArray,
    clientNonce: ByteArray,
    serverNonce: ByteArray,
    client: ByteArray,
    server: ByteArray,
): LanSessionKeys {
    val salt = clientNonce + serverNonce
    val dids = client + server
    return LanSessionKeys(
        clientToServer = derive(lanKey, salt, "lan c2s", dids, 32),
        serverToClient = derive(lanKey, salt, "lan s2c", dids, 32),
    )
}

fun lanFrameAad(seq: Long): ByteArray = tag("lan") + u64(seq)
