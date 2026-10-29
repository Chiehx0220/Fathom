package io.github.aedev.flow.data.profile

import android.content.Context
import java.io.File

/**
 * Everything a profile other than Default keeps on disk, and nothing else. Paths are only ever built from a safe
 * registry id under these fixed roots, so a delete can never reach Default's files, which keep their own names
 * outside them.
 */
internal class ProfileFolders(
    private val filesDir: File,
    noBackupFilesDir: File,
    private val dataDir: File,
) {
    private val roots =
        listOf(File(filesDir, "profile_settings"), File(filesDir, "profile_library"), File(noBackupFilesDir, "profiles"))
    private val sharedPrefsDir = File(dataDir, "shared_prefs")

    fun of(id: String): List<File> {
        require(isSafeProfileId(id)) { "Unsafe profile id" }
        val profile = RunningProfile(id = id, isDefault = false)
        return roots.map { File(it, id) } +
            File(dataDir, "app_webview_$id") +
            ProfileAvatarPhotos.fileIn(filesDir, id) +
            PER_PROFILE_PREFS.map { File(sharedPrefsDir, "${ProfileFiles.sharedPreferencesName(profile, it)}.xml") }
    }

    /** Deletes [id]'s files and returns what it removed. */
    fun delete(id: String): List<File> = of(id).filter { it.exists() && it.deleteRecursively() }

    /** Ids that still have a folder or a preferences file on disk. */
    fun idsOnDisk(): Set<String> {
        val folders = roots.flatMap { root -> root.listFiles(File::isDirectory).orEmpty().map(File::getName) }
        val prefs =
            sharedPrefsDir.listFiles().orEmpty().mapNotNull { file ->
                PER_PROFILE_PREFS.firstNotNullOfOrNull { name ->
                    file.name.removeSurrounding("${name}_", ".xml").takeIf { it != file.name }
                }
            }
        val photos = File(filesDir, ProfileAvatarPhotos.DIR).listFiles().orEmpty().map { it.name.removeSuffix(".webp") }
        return (folders + prefs + photos).filter(::isSafeProfileId).toSet()
    }

    companion object {
        /** SharedPreferences files named per profile through [ProfileFiles.sharedPreferencesName]. */
        val PER_PROFILE_PREFS = listOf("youtube_identity", "music_home_cache_prefs")

        fun of(context: Context): ProfileFolders =
            context.applicationContext.let { ProfileFolders(it.filesDir, it.noBackupFilesDir, it.dataDir) }
    }
}
