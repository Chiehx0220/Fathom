package io.github.aedev.flow.data.profile

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MasterBackupZipTest {
    private val dir = Files.createTempDirectory("master").toFile()

    // The entry names today's importMasterBackup reads; it ignores every other entry.
    private val todaysEntries =
        setOf("app_data.json", "engine_brain.json", "music_brain.json", "recap_stats.json", "favourite_artists.json")

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun slice(
        name: String,
        vararg entries: Pair<String, String>,
    ) = File(dir, name).apply {
        ZipOutputStream(outputStream()).use { zip ->
            entries.forEach { (entry, text) ->
                zip.putNextEntry(ZipEntry(entry))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }

    private fun read(bytes: ByteArray): Map<String, String> =
        buildMap {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                    entry = zip.nextEntry
                }
            }
        }

    private fun master(
        root: File?,
        profiles: Map<String, File>,
        avatars: Map<String, File> = emptyMap(),
    ): Map<String, String> {
        val manifest =
            MasterManifest(
                defaultId = "d",
                profiles = listOf(Profile("d"), Profile("study", name = "Study"), Profile("gaming", name = "Gaming")),
                included = listOfNotNull("d".takeIf { root != null }) + profiles.keys,
            )
        val out = ByteArrayOutputStream()
        MasterBackupZip.write(out, manifest, root, profiles, avatars)
        return read(out.toByteArray())
    }

    @Test
    fun `Default sits at the root and every other profile under its own folder`() {
        val default = slice("d.zip", "app_data.json" to "default data", "engine_brain.json" to "default brain")
        val study = slice("s.zip", "app_data.json" to "study data")
        val photo = File(dir, "study.webp").apply { writeText("webp") }

        val entries = master(default, mapOf("study" to study), mapOf("study" to photo))

        assertThat(entries).containsAtLeast(
            "app_data.json",
            "default data",
            "engine_brain.json",
            "default brain",
            "profiles/study/app_data.json",
            "study data",
            "profile_avatars/study.webp",
            "webp",
        )
    }

    @Test
    fun `today's importer finds Default's data and nothing from another profile`() {
        val default = slice("d.zip", "app_data.json" to "default data", "music_brain.json" to "default music")
        val study = slice("s.zip", "app_data.json" to "study data", "music_brain.json" to "study music")

        val read = master(default, mapOf("study" to study)).filterKeys { it in todaysEntries }

        assertThat(read).containsExactly("app_data.json", "default data", "music_brain.json", "default music")
    }

    @Test
    fun `the manifest says which profiles are in the file`() {
        val study = slice("s.zip", "app_data.json" to "study data")

        val manifest =
            Json {
                ignoreUnknownKeys = true
            }.decodeFromString<MasterManifest>(master(null, mapOf("study" to study)).getValue("manifest.json"))

        assertThat(manifest.format).isEqualTo(MasterManifest.FORMAT)
        assertThat(manifest.profiles.map { it.id }).containsExactly("d", "study", "gaming").inOrder()
        assertThat(manifest.included).containsExactly("study")
    }

    @Test
    fun `each profile's slice comes back out on its own, Default's from the root`() {
        val default = slice("d.zip", "app_data.json" to "default data")
        val study = slice("s.zip", "app_data.json" to "study data")
        val photo = File(dir, "study.webp").apply { writeText("webp") }
        val file = File(dir, "master.zip")
        file.outputStream().use { out ->
            MasterBackupZip.write(
                out,
                MasterManifest(defaultId = "d", profiles = emptyList(), included = emptyList()),
                default,
                mapOf("study" to study),
                mapOf("study" to photo),
            )
        }

        val defaultOut = File(dir, "out/d.zip")
        val studyOut = File(dir, "out/s.zip")
        val photoOut = File(dir, "out/s.webp")

        assertThat(MasterBackupZip.extractSlice(file, null, defaultOut)).isTrue()
        assertThat(MasterBackupZip.extractSlice(file, "study", studyOut)).isTrue()
        assertThat(MasterBackupZip.extractSlice(file, "gaming", File(dir, "out/g.zip"))).isFalse()
        assertThat(MasterBackupZip.extractAvatar(file, "study", photoOut)).isTrue()
        assertThat(read(defaultOut.readBytes())).containsExactly("app_data.json", "default data")
        assertThat(read(studyOut.readBytes())).containsExactly("app_data.json", "study data")
        assertThat(photoOut.readText()).isEqualTo("webp")
    }

    @Test
    fun `a master backup from before profiles has no manifest`() {
        assertThat(MasterBackupZip.readManifest(slice("old.zip", "app_data.json" to "old data"))).isNull()
    }
}
