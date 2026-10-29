package io.github.aedev.flow.data.profile

import android.content.Context
import java.io.File

/**
 * Where a store keeps its file for the profile this process runs as. Default and [StoreScope.DEVICE] stores keep
 * the exact paths they had before profiles existed, so upgrading moves nothing and an older build still finds them.
 */
object ProfileFiles {
    fun preferences(
        context: Context,
        scope: StoreScope,
        name: String,
    ): File = dataStore(context, scope, "$name.preferences_pb")

    /** A DataStore file, which Default keeps in `files/datastore/`. */
    fun dataStore(
        context: Context,
        scope: StoreScope,
        fileName: String,
    ): File = file(context, scope, "datastore/$fileName")

    /** A file Default keeps directly under `files/`. */
    fun file(
        context: Context,
        scope: StoreScope,
        path: String,
    ): File {
        val app = context.applicationContext
        return File(root(app.filesDir, app.noBackupFilesDir, ActiveProfile.get(app), scope), path)
    }

    /**
     * What Room opens for the database [name]: the bare name for Default, which Room keeps in `databases/`, or an
     * absolute path in another profile's library folder.
     */
    fun database(
        context: Context,
        name: String,
    ): String {
        val app = context.applicationContext
        return database(app.filesDir, app.noBackupFilesDir, ActiveProfile.get(app), name)?.apply { parentFile?.mkdirs() }?.absolutePath
            ?: name
    }

    /** Null for Default, whose database stays where Room has always put it. */
    internal fun database(
        filesDir: File,
        noBackupFilesDir: File,
        profile: RunningProfile,
        name: String,
    ): File? =
        if (profile.isDefault) null else File(root(filesDir, noBackupFilesDir, profile, StoreScope.PROFILE_LIBRARY), "databases/$name")

    /** A SharedPreferences file for the running profile: Default keeps [name], another profile gets `name_<id>`. */
    fun sharedPreferencesName(
        context: Context,
        name: String,
    ): String = sharedPreferencesName(ActiveProfile.get(context.applicationContext), name)

    internal fun sharedPreferencesName(
        profile: RunningProfile,
        name: String,
    ): String {
        if (profile.isDefault) return name
        require(isSafeProfileId(profile.id)) { "Unsafe profile id" }
        return "${name}_${profile.id}"
    }

    /** Where the running profile keeps what Default keeps directly under `no_backup/`: private, never backed up. */
    fun noBackupRoot(context: Context): File {
        val app = context.applicationContext
        return noBackupRoot(app.noBackupFilesDir, ActiveProfile.get(app))
    }

    internal fun preferences(
        filesDir: File,
        noBackupFilesDir: File,
        profile: RunningProfile,
        scope: StoreScope,
        name: String,
    ): File = File(root(filesDir, noBackupFilesDir, profile, scope), "datastore/$name.preferences_pb")

    internal fun noBackupRoot(
        noBackupFilesDir: File,
        profile: RunningProfile,
    ): File = if (profile.isDefault) noBackupFilesDir else privateRoot(noBackupFilesDir, profile)

    // DEFAULT_ONLY resolves to the private folder rather than Default's file, so a sync path that runs in another
    // profile by mistake writes to a file nobody reads instead of into Default's sync state.
    internal fun root(
        filesDir: File,
        noBackupFilesDir: File,
        profile: RunningProfile,
        scope: StoreScope,
    ): File {
        if (profile.isDefault || scope == StoreScope.DEVICE) return filesDir
        require(isSafeProfileId(profile.id)) { "Unsafe profile id" }
        return when (scope) {
            StoreScope.PROFILE_SETTINGS -> File(filesDir, "profile_settings/${profile.id}")
            StoreScope.PROFILE_LIBRARY -> File(filesDir, "profile_library/${profile.id}")
            StoreScope.PROFILE_PRIVATE, StoreScope.DEFAULT_ONLY -> privateRoot(noBackupFilesDir, profile)
            StoreScope.DEVICE -> filesDir
        }
    }

    private fun privateRoot(
        noBackupFilesDir: File,
        profile: RunningProfile,
    ): File {
        require(isSafeProfileId(profile.id)) { "Unsafe profile id" }
        return File(noBackupFilesDir, "profiles/${profile.id}")
    }
}

private val SafeProfileId = Regex("[A-Za-z0-9-]{1,64}")

/** Profile ids become folder names, so anything that could step outside its folder is refused. */
internal fun isSafeProfileId(id: String?): Boolean = id != null && SafeProfileId.matches(id)
