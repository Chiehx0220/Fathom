package io.github.aedev.flow.data.profile

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.video.downloader.collection.DownloadedCollections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Creates, renames and deletes profiles. Switching to one restarts the process (see ProfileSwitcher). */
@Singleton
class ProfileRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val slices: dagger.Lazy<ProfileSlices>,
        private val collections: dagger.Lazy<DownloadedCollections>,
        private val photos: ProfileAvatarPhotos,
    ) {
        private val registry: ProfileRegistry get() = ActiveProfile.registry(context)

        val state: Flow<ProfileRegistryState> get() = registry.state

        /** The profile this process runs as. */
        fun running(): RunningProfile = ActiveProfile.get(context)

        /**
         * Registers a profile and, when [copy] names parts, writes them from the running profile as its seed. A seed
         * that can't be written takes the new entry with it, so a half-copied profile never appears.
         */
        suspend fun create(
            name: String,
            colorKey: String = "",
            avatar: ProfileAvatar = ProfileAvatar(),
            copy: Set<SlicePart> = emptySet(),
        ): CreateResult {
            require(name.isNotBlank()) { "A new profile needs a name" }
            val profile = registry.add(name.trim(), colorKey, avatar) ?: return CreateResult.LimitReached
            if (copy.isEmpty()) return CreateResult.Created(profile)
            return slices.get().writeSeed(profile.id, copy).fold(
                onSuccess = { CreateResult.Created(profile) },
                onFailure = { cause ->
                    withContext(Dispatchers.IO) {
                        registry.remove(profile.id)
                        ProfileFolders.of(context).delete(profile.id)
                    }
                    CreateResult.CopyFailed(cause)
                },
            )
        }

        suspend fun rename(
            id: String,
            name: String,
        ): Boolean = registry.rename(id, name.trim())

        /**
         * Removes an inactive profile and everything it kept on disk. Its files are closed: only the running
         * profile's are open in this process.
         */
        suspend fun setAskWhosWatching(ask: Boolean) = registry.setAskWhosWatching(ask)

        suspend fun setSplashTone(
            id: String,
            tone: String,
        ) = registry.setSplashTone(id, tone)

        suspend fun setAppearance(
            id: String,
            colorKey: String,
            avatar: ProfileAvatar,
        ): Boolean = registry.setAppearance(id, colorKey, avatar)

        /** Makes the picked image [id]'s avatar; false when it couldn't be read, which leaves the avatar as it was. */
        suspend fun setPhoto(
            id: String,
            source: Uri,
        ): Boolean {
            val profile =
                registry.state
                    .first()
                    .profiles
                    .firstOrNull { it.id == id } ?: return false
            val version = photos.save(id, source) ?: return false
            return registry.setAppearance(id, profile.colorKey, profile.avatar.copy(kind = ProfileAvatar.PHOTO, photoVersion = version))
        }

        /** Goes back to the initial and deletes the photo. */
        suspend fun removePhoto(id: String) {
            val profile =
                registry.state
                    .first()
                    .profiles
                    .firstOrNull { it.id == id } ?: return
            registry.setAppearance(id, profile.colorKey, ProfileAvatar(emoji = profile.avatar.emoji))
            withContext(Dispatchers.IO) { photos.delete(id) }
        }

        suspend fun delete(id: String): Boolean =
            withContext(Dispatchers.IO) {
                if (!isSafeProfileId(id) || id == ActiveProfile.get(context).id) return@withContext false
                if (!registry.remove(id)) return@withContext false
                ProfileFolders.of(context).delete(id)
                collections.get().forgetProfile(id)
                true
            }
    }

sealed interface CreateResult {
    data class Created(
        val profile: Profile,
    ) : CreateResult

    /** There are already [ProfileRegistry.MAX_PROFILES]. */
    data object LimitReached : CreateResult

    data class CopyFailed(
        val cause: Throwable,
    ) : CreateResult
}
