package io.github.aedev.flow.sync.canonical

import io.github.aedev.flow.sync.identity.Hlc
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement

/**
 * Platform-neutral canonical records exchanged over FLOW-SYNC/1.
 *
 * Conventions: epoch **milliseconds** for all times; `progress` is a 0..1 fraction;
 * `durationSeconds` is integer seconds; deletions are **tombstones** (`deleted=true`), never
 * omissions; every mergeable record carries an `hlc` string. Android maps its
 * Room/DataStore/brain values to/from these in `sync/mapping`.
 *
 * These types are the unit the merge engine operates on, so they are deliberately decoupled
 * from both DB schemas. Keep field names in sync with the desktop `canonical.rs`.
 */

@Serializable
data class CanonicalWatchHistory(
    val videoId: String,
    val title: String = "",
    val channelName: String = "",
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val watchedAtMs: Long = 0,
    val progress: Double = 0.0,
    val durationSeconds: Long = 0,
    val isMusic: Boolean = false,
    val isShort: Boolean = false,
    val hlc: String = "",
    val deleted: Boolean = false,
    /** org.schabi.newpipe.extractor.ServiceList id. 0 = YouTube. */
    val serviceId: Int = 0,
)

@Serializable
data class CanonicalPlaylistItem(
    val videoId: String,
    /** Ascending display rank (0-based).*/
    val position: Long = 0,
    val addedAtMs: Long = 0,
    val deleted: Boolean = false,
    val title: String = "",
    val channelName: String = "",
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val durationSeconds: Long = 0,
    val isMusic: Boolean = false,
    val hlc: String = "",
)

@Serializable
data class CanonicalPlaylist(
    val syncId: String,
    val origin: String = ORIGIN_LOCAL,
    val youtubeId: String? = null,
    val title: String = "",
    val description: String = "",
    val isMusic: Boolean = false,
    val isUserCreated: Boolean = true,
    val isProtected: Boolean = false,
    val createdAtMs: Long = 0,
    val updatedHlc: String = "",
    val deleted: Boolean = false,
    val items: List<CanonicalPlaylistItem> = emptyList(),
) {
    companion object {
        const val ORIGIN_LOCAL = "local"
        const val ORIGIN_YOUTUBE = "youtube"

        /** Reserved id for the cross-platform Watch Later playlist */
        const val RESERVED_WATCH_LATER = "reserved:watch-later"
    }
}

/** Minimal display metadata for a like (desktop §6.3 `meta`). `artist` ⇄ Android `channelName`. */
@Serializable
data class CanonicalLikeMeta(
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String = "",
)

@Serializable
data class CanonicalLike(
    val kind: String, // "video" | "music"
    val id: String,
    val state: String, // "liked" | "disliked" | "none"
    val updatedAtMs: Long = 0,
    val hlc: String = "",
    val meta: CanonicalLikeMeta = CanonicalLikeMeta(),
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    /** org.schabi.newpipe.extractor.ServiceList id. 0 = YouTube. */
    val serviceId: Int = 0,
) {
    companion object {
        const val KIND_VIDEO = "video"
        const val KIND_MUSIC = "music"
        const val STATE_LIKED = "liked"
        const val STATE_DISLIKED = "disliked"
        const val STATE_NONE = "none"
    }
}

/** A single synced setting. [value] is a typed JSON primitive; the mapper coerces per key. */
@Serializable
data class CanonicalSetting(
    val key: String,
    val value: JsonElement,
    val hlc: String = "",
)

@Serializable
data class CanonicalNote(
    val id: String,
    val targetId: String = "",
    val kind: String = "",
    val text: String = "",
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
    val title: String? = null,
    val channelName: String? = null,
    val channelId: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int? = null,
    val channelAvatarUrl: String? = null,
    val channelHandle: String? = null,
    val subscriberCountText: String? = null,
)

@Serializable
data class CanonicalSubscriptionGroup(
    val name: String,
    val channelIds: List<String> = emptyList(),
    val sortOrder: Int = 0,
    val hlc: String = "",
    val deleted: Boolean = false,
)

/**
 * One channel the user follows. Unsubscribing is a **tombstone** (`deleted=true`) carrying the
 * moment it happened, not an omission — otherwise the peer's copy simply re-subscribes the channel
 * on the next merge.
 *
 * Per-device concerns (notification opt-in, feed-fetch bookkeeping) are deliberately not on the
 * wire: whether a channel should buzz *this* phone is not a property of the subscription.
 */
@Serializable
data class CanonicalSubscribedChannel(
    val channelId: String,
    val name: String = "",
    val avatarUrl: String = "",
    val subscribedAtMs: Long = 0,
    val isMusic: Boolean = false,
    val hlc: String = "",
    val deleted: Boolean = false,
    /** org.schabi.newpipe.extractor.ServiceList id. 0 = YouTube. */
    val serviceId: Int = 0,
)

// --- Brain CRDT primitives (wire forms must match the desktop's `canonical.rs` byte for byte) ---

/**
 * G-Counter: per-device sub-counts; value = sum; merge = per-device max.
 *
 * The **wire form is the `{device: count}` map itself** (desktop plan §6.5), not an object wrapping
 * it. Android used to emit `{"perDevice":{...}}`, which the desktop rejected outright with
 * `invalid type: map, expected u64` — the hard parse failure behind the "brain sync fails" reports.
 */
@Serializable(with = GCounterSerializer::class)
data class GCounter(
    val perDevice: Map<String, Long> = emptyMap(),
) {
    fun sum(): Long = perDevice.values.sum()

    fun merge(other: GCounter): GCounter {
        if (other.perDevice.isEmpty()) return this
        if (perDevice.isEmpty()) return other
        val out = HashMap(perDevice)
        for ((d, c) in other.perDevice) {
            out[d] = maxOf(out[d] ?: Long.MIN_VALUE, c)
        }
        return GCounter(out)
    }
}

object GCounterSerializer : KSerializer<GCounter> {
    private val delegate = MapSerializer(String.serializer(), Long.serializer())
    override val descriptor: SerialDescriptor = SerialDescriptor("GCounter", delegate.descriptor)

    override fun serialize(
        encoder: Encoder,
        value: GCounter,
    ) = delegate.serialize(encoder, value.perDevice)

    override fun deserialize(decoder: Decoder): GCounter = GCounter(delegate.deserialize(decoder))
}

/**
 * Observed-remove set: member to the HLC of its latest add / latest remove. A member is present iff
 * its add stamp is >= its remove stamp (add wins on tie).
 *
 * The tombstones are the point: a plain `Set<String>` can express "blocked" but has no way to say
 * "explicitly unblocked", so an unblock on one device could never propagate to the other.
 */
@Serializable
data class OrSet(
    val adds: Map<String, String> = emptyMap(),
    val removes: Map<String, String> = emptyMap(),
) {
    fun contains(member: String): Boolean {
        val added = adds[member] ?: return false
        val removed = removes[member] ?: return true
        return Hlc.compareEncoded(added, removed) >= 0
    }

    fun members(): Set<String> = adds.keys.filterTo(LinkedHashSet()) { contains(it) }

    fun add(
        member: String,
        hlc: String,
    ): OrSet = copy(adds = adds + (member to Hlc.maxEncoded(adds[member].orEmpty(), hlc)))

    fun remove(
        member: String,
        hlc: String,
    ): OrSet = copy(removes = removes + (member to Hlc.maxEncoded(removes[member].orEmpty(), hlc)))

    fun merge(other: OrSet): OrSet {
        if (other.adds.isEmpty() && other.removes.isEmpty()) return this
        if (adds.isEmpty() && removes.isEmpty()) return other
        return OrSet(mergeStamps(adds, other.adds), mergeStamps(removes, other.removes))
    }

    private fun mergeStamps(
        a: Map<String, String>,
        b: Map<String, String>,
    ): Map<String, String> {
        if (b.isEmpty()) return a
        if (a.isEmpty()) return b
        val out = HashMap(a)
        for ((k, v) in b) out[k] = Hlc.maxEncoded(out[k].orEmpty(), v)
        return out
    }
}

/** Last-write-wins register; the higher HLC wins, the device id inside it breaking ties. */
@Serializable
data class Lww<T>(
    val value: T,
    val hlc: String = "",
) {
    fun merge(other: Lww<T>): Lww<T> = if (Hlc.compareEncoded(other.hlc, hlc) > 0) other else this
}
