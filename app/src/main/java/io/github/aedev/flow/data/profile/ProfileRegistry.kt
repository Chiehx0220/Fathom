package io.github.aedev.flow.data.profile

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import io.github.aedev.flow.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** One profile as the registry keeps it. A blank [name] is shown as the localized default name. */
@Serializable
data class Profile(
    val id: String,
    val name: String = "",
    val colorKey: String = "",
    val avatar: ProfileAvatar = ProfileAvatar(),
    val createdAt: Long = 0L,
    /** The tone this profile's theme last gave the splash, so a switch can open the next launch in it. */
    val splashTone: String = "",
)

/** The name to show: Default's is blank until it is renamed, and shows the localized "Default". */
fun Profile.displayName(context: Context): String = name.ifBlank { context.getString(R.string.profile_default_name) }

/**
 * Flat rather than sealed, so an older build that meets an avatar kind it doesn't know shows the initial instead
 * of treating the whole registry as corrupt.
 */
@Serializable
data class ProfileAvatar(
    val kind: String = INITIAL,
    val emoji: String = "",
    val photoVersion: Long = 0L,
) {
    companion object {
        const val INITIAL = "initial"
        const val EMOJI = "emoji"
        const val PHOTO = "photo"
    }
}

@Serializable
data class ProfileRegistryState(
    val profiles: List<Profile> = emptyList(),
    val defaultId: String? = null,
    val activeId: String? = null,
    /** Set by a switch, cleared by the first process of the profile switched to once it has caught up. */
    val postSwitch: Boolean = false,
    /** On a TV with two or more profiles, a cold start first asks who is watching. */
    val askWhosWatching: Boolean = true,
)

/**
 * The profile a process runs as. [id] is blank only when the registry could not be read; Default's paths never use
 * it. [afterSwitch] is true in the first process after a switch to it.
 */
data class RunningProfile(
    val id: String,
    val isDefault: Boolean,
    val afterSwitch: Boolean = false,
)

/**
 * The profiles on this install and which one is active, in `datastore/profiles_registry.json`. It is the one store
 * every profile shares. DataStore allows one instance per file, so [ActiveProfile] owns the only one.
 */
class ProfileRegistry internal constructor(
    file: File,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val store: DataStore<ProfileRegistryState> =
        DataStoreFactory.create(
            serializer = RegistrySerializer,
            corruptionHandler =
                ReplaceFileCorruptionHandler { error ->
                    Log.e(TAG, "Profile registry corrupted, starting over with Default", error)
                    ProfileRegistryState()
                },
            scope = scope,
            produceFile = { file },
        )

    val state: Flow<ProfileRegistryState> = store.data

    /** The active profile. Creates Default on first run, and falls back to it when the active profile is gone. */
    suspend fun running(): RunningProfile {
        val stored = store.data.first()
        val complete = if (stored.isComplete()) stored else store.updateData { it.completed(newId, now) }
        return complete.running()
    }

    /** Adds a profile and returns it, or null when there are already [MAX_PROFILES]. */
    suspend fun add(
        name: String,
        colorKey: String,
        avatar: ProfileAvatar,
    ): Profile? {
        var added: Profile? = null
        store.updateData { stored ->
            val complete = stored.completed(newId, now)
            if (complete.profiles.size >= MAX_PROFILES) return@updateData complete
            val profile = Profile(id = newId(), name = name, colorKey = colorKey, avatar = avatar, createdAt = now())
            added = profile
            complete.copy(profiles = complete.profiles + profile)
        }
        return added
    }

    /** Renames a profile; only Default may go back to a blank name, which shows the localized default. */
    suspend fun rename(
        id: String,
        name: String,
    ): Boolean {
        var renamed = false
        store.updateData { stored ->
            val complete = stored.completed(newId, now)
            if (name.isBlank() && id != complete.defaultId) return@updateData complete
            if (complete.profiles.none { it.id == id }) return@updateData complete
            renamed = true
            complete.copy(profiles = complete.profiles.map { if (it.id == id) it.copy(name = name) else it })
        }
        return renamed
    }

    /**
     * Adds the profiles a master backup brings that this device doesn't have, under their own ids. Nothing is added
     * when they wouldn't all fit under [MAX_PROFILES]; profiles already here keep how they look on this device.
     */
    suspend fun addRestored(incoming: List<Profile>): Boolean {
        var added = false
        store.updateData { stored ->
            val complete = stored.completed(newId, now)
            val missing = incoming.filter { profile -> isSafeProfileId(profile.id) && complete.profiles.none { it.id == profile.id } }
            if (complete.profiles.size + missing.size > MAX_PROFILES) return@updateData complete
            added = true
            complete.copy(profiles = complete.profiles + missing)
        }
        return added
    }

    /** Changes how a profile looks: its colour and its avatar. */
    suspend fun setAppearance(
        id: String,
        colorKey: String,
        avatar: ProfileAvatar,
    ): Boolean {
        var changed = false
        store.updateData { stored ->
            val complete = stored.completed(newId, now)
            if (complete.profiles.none { it.id == id }) return@updateData complete
            changed = true
            complete.copy(profiles = complete.profiles.map { if (it.id == id) it.copy(colorKey = colorKey, avatar = avatar) else it })
        }
        return changed
    }

    suspend fun setSplashTone(
        id: String,
        tone: String,
    ) {
        store.updateData { stored ->
            val complete = stored.completed(newId, now)
            if (complete.profiles.none { it.id == id && it.splashTone != tone }) return@updateData complete
            complete.copy(profiles = complete.profiles.map { if (it.id == id) it.copy(splashTone = tone) else it })
        }
    }

    suspend fun setAskWhosWatching(ask: Boolean) {
        store.updateData { stored -> stored.completed(newId, now).copy(askWhosWatching = ask) }
    }

    /** Removes a profile from the registry. Default and the active profile are never removed. */
    suspend fun remove(id: String): Boolean {
        var removed = false
        store.updateData { stored ->
            val complete = stored.completed(newId, now)
            if (id == complete.defaultId || id == complete.activeId || complete.profiles.none { it.id == id }) return@updateData complete
            removed = true
            complete.copy(profiles = complete.profiles.filterNot { it.id == id })
        }
        return removed
    }

    /** Makes [id] the profile the next process runs as. The caller restarts the process. */
    suspend fun setActive(id: String): Boolean {
        var set = false
        store.updateData { stored ->
            val complete = stored.completed(newId, now)
            if (!isSafeProfileId(id) || complete.profiles.none { it.id == id }) return@updateData complete
            set = true
            complete.copy(activeId = id, postSwitch = true)
        }
        return set
    }

    /** True once, in the first process after a switch, which then redoes what a switch leaves stale. */
    suspend fun finishPostSwitch(): Boolean {
        var switched = false
        store.updateData { stored ->
            switched = stored.postSwitch
            if (switched) stored.copy(postSwitch = false) else stored
        }
        return switched
    }

    /** After a restore: the app reopens in Default, whichever profile was active when the backup was made. */
    suspend fun reopenInDefault(): ProfileRegistryState =
        store.updateData { stored -> stored.completed(newId, now).let { it.copy(activeId = it.defaultId) } }

    companion object {
        const val FILE_NAME = "profiles_registry.json"
        const val MAX_PROFILES = 5
        private const val TAG = "ProfileRegistry"

        /**
         * The active profile read straight from [file] with the store's own format, or null when the file is
         * missing, unreadable or incomplete and [running] has to repair it. Process start uses this: opening the
         * DataStore there cost about 30 ms more on the main thread, and nothing needs writing on a normal start.
         * DataStore replaces the file by rename, so a reader never sees a partial write.
         */
        fun peek(file: File): RunningProfile? =
            try {
                file
                    .takeIf(File::isFile)
                    ?.readText()
                    ?.let { RegistryJson.decodeFromString(ProfileRegistryState.serializer(), it) }
                    ?.takeIf(ProfileRegistryState::isComplete)
                    ?.running()
            } catch (e: IOException) {
                null
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}

private fun ProfileRegistryState.running() =
    RunningProfile(id = checkNotNull(activeId), isDefault = activeId == defaultId, afterSwitch = postSwitch)

internal fun ProfileRegistryState.isComplete(): Boolean =
    isSafeProfileId(defaultId) &&
        isSafeProfileId(activeId) &&
        profiles.any { it.id == defaultId } &&
        profiles.any { it.id == activeId }

internal fun ProfileRegistryState.completed(
    newId: () -> String,
    now: () -> Long,
): ProfileRegistryState {
    val withDefault =
        if (isSafeProfileId(defaultId) && profiles.any { it.id == defaultId }) {
            this
        } else {
            val id = newId()
            copy(profiles = listOf(Profile(id = id, createdAt = now())) + profiles, defaultId = id)
        }
    return if (isSafeProfileId(withDefault.activeId) && withDefault.profiles.any { it.id == withDefault.activeId }) {
        withDefault
    } else {
        withDefault.copy(
            activeId = withDefault.defaultId,
        )
    }
}

private val RegistryJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

private object RegistrySerializer : Serializer<ProfileRegistryState> {
    override val defaultValue = ProfileRegistryState()

    override suspend fun readFrom(input: InputStream): ProfileRegistryState {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            RegistryJson.decodeFromString(ProfileRegistryState.serializer(), text)
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable profile registry", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Unreadable profile registry", e)
        }
    }

    override suspend fun writeTo(
        t: ProfileRegistryState,
        output: OutputStream,
    ) {
        output.write(RegistryJson.encodeToString(ProfileRegistryState.serializer(), t).encodeToByteArray())
    }
}
