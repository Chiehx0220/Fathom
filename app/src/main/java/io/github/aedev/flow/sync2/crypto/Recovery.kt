package io.github.aedev.flow.sync2.crypto

import cash.z.ecc.android.bip39.Mnemonics

private const val SECRET_SIZE = 32

fun recoveryWords(secret: ByteArray): String = Mnemonics.MnemonicCode(secret).joinToString(" ")

/** `null` for an unknown word, a wrong checksum, or anything but 24 words. */
fun recoverySecret(words: String): ByteArray? {
    val normalized =
        words
            .trim()
            .split(Regex("\\s+"))
            .joinToString(" ")
            .lowercase()
    return runCatching {
        Mnemonics.MnemonicCode(normalized).run {
            validate()
            toEntropy()
        }
    }.getOrNull()?.takeIf { it.size == SECRET_SIZE }
}

fun recoveryKeys(secret: ByteArray): MemberSecrets {
    val derived = { label: String -> derive(secret, ByteArray(0), label, ByteArray(0), 32) }
    return MemberSecrets(derived("recovery sig"), derived("recovery kem"))
}
