package io.github.aedev.flow.data.video.downloader.collection

import io.github.aedev.flow.data.local.entity.DownloadCollectionEntity
import io.github.aedev.flow.data.local.entity.DownloadCollectionKind
import io.github.aedev.flow.data.profile.RunningProfile
import io.github.aedev.flow.data.profile.isSafeProfileId

/**
 * Downloads are shared by every profile, but a profile's own playlists are not, and Watch Later or two restored
 * copies of one playlist share an id across profiles. So a collection of a profile's own playlist is stored as
 * `<profileId>:<playlistId>` outside Default; Default keeps the bare id it always had. Pages only see playlist ids.
 */
internal object CollectionOwners {
    private const val SEPARATOR = ':'

    fun storedId(
        playlistId: String,
        ownPlaylist: Boolean,
        profile: RunningProfile,
    ): String = if (ownPlaylist && !profile.isDefault) "${profile.id}$SEPARATOR$playlistId" else playlistId

    /** The stored ids [playlistId] may have here, the running profile's own first. */
    fun candidates(
        playlistId: String,
        profile: RunningProfile,
    ): List<String> = if (profile.isDefault) listOf(playlistId) else listOf("${profile.id}$SEPARATOR$playlistId", playlistId)

    /** What every collection of [profileId]'s own playlists starts with. */
    fun prefixOf(profileId: String): String = "$profileId$SEPARATOR"

    fun playlistId(storedId: String): String = owner(storedId)?.let { storedId.removePrefix("$it$SEPARATOR") } ?: storedId

    /** Another profile's own playlist is left out; a bare local playlist is Default's. */
    fun isVisible(
        collection: DownloadCollectionEntity,
        profile: RunningProfile,
    ): Boolean {
        val owner = owner(collection.id)
        return when {
            owner != null -> owner == profile.id
            collection.kind == DownloadCollectionKind.LOCAL_PLAYLIST -> profile.isDefault
            else -> true
        }
    }

    /** The collection as its page knows it: under its playlist id. */
    fun DownloadCollectionEntity.asPage(): DownloadCollectionEntity = copy(id = playlistId(id))

    private fun owner(storedId: String): String? = storedId.substringBefore(SEPARATOR, missingDelimiterValue = "").takeIf(::isSafeProfileId)
}
