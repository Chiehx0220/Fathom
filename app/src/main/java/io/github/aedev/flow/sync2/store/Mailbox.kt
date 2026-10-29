package io.github.aedev.flow.sync2.store

/**
 * The pairing mailbox (spec/store.md, Pairing mailbox): a short-lived slot where two devices
 * exchange sealed pairing messages before the new one has any roster key.
 */
interface Mailbox {
    suspend fun openSlot(slot: String)

    suspend fun putMessage(
        slot: String,
        name: String,
        bytes: ByteArray,
    )

    suspend fun getMessage(
        slot: String,
        name: String,
    ): ByteArray?

    suspend fun closeSlot(slot: String)
}
