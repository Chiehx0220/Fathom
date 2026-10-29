package io.github.aedev.flow.sync2.crypto

/** A member's key pairs: this device's own, or the recovery key while it is in use. */
class MemberSecrets(
    val sigSeed: ByteArray,
    val kemSecret: ByteArray,
) {
    val sigPublic: ByteArray = signingPublicKey(sigSeed)
    val kemPublic: ByteArray = agreementPublicKey(kemSecret)
    val did: ByteArray = memberId(sigPublic, kemPublic)

    fun wipe() {
        sigSeed.fill(0)
        kemSecret.fill(0)
    }
}

fun memberId(
    sigPublic: ByteArray,
    kemPublic: ByteArray,
): ByteArray = labeledId("device", sigPublic + kemPublic, 16)
