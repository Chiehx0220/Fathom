package io.github.aedev.flow.data.profile

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Which profiles a master backup holds, and which it couldn't, so a restore can say so. */
@Serializable
data class MasterManifest(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val defaultId: String,
    val profiles: List<Profile>,
    val included: List<String>,
) {
    companion object {
        const val FORMAT = "flow-master"
        const val VERSION = 1
    }
}

/**
 * Lays out a master backup. Default's slice stays at the root under the names an older app restores, so an older
 * app still restores Default and ignores the rest; every other profile's slice goes under `profiles/<id>/`, photos
 * under `profile_avatars/`, and [MANIFEST] says what is there.
 */
internal object MasterBackupZip {
    const val MANIFEST = "manifest.json"
    const val PROFILES_DIR = "profiles/"
    const val AVATARS_DIR = "profile_avatars/"

    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    /** The manifest of [zip], or null for a master backup from before profiles, which holds one profile at its root. */
    fun readManifest(zip: File): MasterManifest? =
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                if (entry.name == MANIFEST) {
                    return@use runCatching {
                        json.decodeFromString(
                            MasterManifest.serializer(),
                            input.readBytes().toString(Charsets.UTF_8),
                        )
                    }.getOrNull()
                }
                entry = input.nextEntry
            }
            null
        }

    /** One profile's slice out of [zip]: Default's from the root when [profileId] is null, another's from its folder. */
    fun extractSlice(
        zip: File,
        profileId: String?,
        target: File,
    ): Boolean {
        val prefix = profileId?.let { "$PROFILES_DIR$it/" }.orEmpty()
        var found = false
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream()).use { out ->
            ZipInputStream(zip.inputStream().buffered()).use { input ->
                var entry = input.nextEntry
                while (entry != null) {
                    val name = entry.name.removePrefix(prefix)
                    if (!entry.isDirectory && entry.name.startsWith(prefix) && '/' !in name && name != MANIFEST) {
                        out.putNextEntry(ZipEntry(name))
                        input.copyTo(out)
                        out.closeEntry()
                        found = true
                    }
                    entry = input.nextEntry
                }
            }
        }
        if (!found) target.delete()
        return found
    }

    /** [profileId]'s photo out of [zip] into [target]; false when the backup has none. */
    fun extractAvatar(
        zip: File,
        profileId: String,
        target: File,
    ): Boolean =
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                if (entry.name == "$AVATARS_DIR$profileId.webp") {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { input.copyTo(it) }
                    return@use true
                }
                entry = input.nextEntry
            }
            false
        }

    fun write(
        out: OutputStream,
        manifest: MasterManifest,
        root: File?,
        profiles: Map<String, File>,
        avatars: Map<String, File>,
    ) {
        ZipOutputStream(out).use { zip ->
            root?.let { copyEntries(it, zip, prefix = "") }
            profiles.forEach { (id, slice) -> copyEntries(slice, zip, prefix = "$PROFILES_DIR$id/") }
            avatars.forEach { (id, photo) ->
                zip.putNextEntry(ZipEntry("$AVATARS_DIR$id.webp"))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(json.encodeToString(MasterManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
        }
    }

    private fun copyEntries(
        slice: File,
        zip: ZipOutputStream,
        prefix: String,
    ) {
        ZipInputStream(slice.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    zip.putNextEntry(ZipEntry(prefix + entry.name))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
                entry = input.nextEntry
            }
        }
    }
}
