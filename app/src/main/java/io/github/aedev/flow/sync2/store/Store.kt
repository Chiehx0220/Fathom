package io.github.aedev.flow.sync2.store

import io.github.aedev.flow.sync2.roster.Entry
import kotlinx.serialization.Serializable
import java.io.IOException

/** What the sync engine needs from a store. The Flow server implements it; other backends will too. */
interface Store {
    suspend fun rosterAfter(after: Long): List<Entry>

    suspend fun appendRoster(
        head: String,
        entry: Entry,
        wraps: Map<String, String>,
        chain: String?,
    ): Appended

    suspend fun key(
        epoch: Int,
        name: String,
    ): ByteArray?

    suspend fun putObject(
        path: String,
        bytes: ByteArray,
    )

    suspend fun getObject(path: String): ByteArray?

    /** Deletes an object; one that is already gone counts as deleted. */
    suspend fun deleteObject(path: String)

    /** Objects that arrived after [cursor], and the cursor to pass next time. */
    suspend fun changes(cursor: Long): Pair<List<ChangedObject>, Long>
}

/** Whether an append landed, or lost to another device's append at the same head. */
sealed interface Appended {
    data object Done : Appended

    data class Conflict(
        val head: String,
    ) : Appended
}

/** A device asking to join with the admin token, by the name it gave. */
@Serializable
data class JoinRequest(
    val id: String,
    val name: String,
    val expires: Long,
)

@Serializable
data class ChangedObject(
    val path: String,
    val size: Long,
)

sealed class StoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Insecure : StoreException("The server must use HTTPS unless it is on a private network")

    class Network(
        cause: IOException,
    ) : StoreException("Could not reach the server", cause)

    class Refused(
        val status: Int,
        val code: String,
    ) : StoreException("The server refused the request: $code ($status)") {
        val rateLimited: Boolean get() = status == 429
    }

    class InstanceChanged : StoreException("This is not the server instance this device paired with")

    class Malformed : StoreException("The server sent an invalid response")
}
