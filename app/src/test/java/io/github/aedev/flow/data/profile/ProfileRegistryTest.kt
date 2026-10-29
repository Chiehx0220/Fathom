package io.github.aedev.flow.data.profile

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ProfileRegistryTest {
    private val dir = createTempDirectory("registry").toFile()
    private val file = File(dir, "datastore/${ProfileRegistry.FILE_NAME}")

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun <T> withRegistry(
        newId: () -> String = { error("No profile should be created") },
        block: suspend (ProfileRegistry) -> T,
    ): T =
        runBlocking {
            val job = Job()
            try {
                block(ProfileRegistry(file, CoroutineScope(Dispatchers.IO + job), newId, now = { 1_000L }))
            } finally {
                job.cancelAndJoin()
            }
        }

    @Test
    fun `the first run creates Default and makes it active`() {
        val running = withRegistry(newId = { "default-id" }) { it.running() }

        assertThat(running).isEqualTo(RunningProfile(id = "default-id", isDefault = true))
        val state = withRegistry { it.state.first() }
        assertThat(state.profiles).containsExactly(Profile(id = "default-id", createdAt = 1_000L))
        assertThat(state.defaultId).isEqualTo("default-id")
        assertThat(state.activeId).isEqualTo("default-id")
    }

    @Test
    fun `a later start reads the same Default without writing`() {
        withRegistry(newId = { "default-id" }) { it.running() }
        val written = file.readText()

        val running = withRegistry { it.running() }

        assertThat(running).isEqualTo(RunningProfile(id = "default-id", isDefault = true))
        assertThat(file.readText()).isEqualTo(written)
    }

    @Test
    fun `a corrupt file starts over with a new Default`() {
        file.parentFile!!.mkdirs()
        file.writeText("{ not json")

        val running = withRegistry(newId = { "fresh-id" }) { it.running() }

        assertThat(running).isEqualTo(RunningProfile(id = "fresh-id", isDefault = true))
    }

    @Test
    fun `another active profile runs as that profile`() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"study"}""")

        assertThat(withRegistry { it.running() }).isEqualTo(RunningProfile(id = "study", isDefault = false))
    }

    @Test
    fun `an active profile that is gone falls back to Default and keeps the others`() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"deleted"}""")

        val running = withRegistry { it.running() }

        assertThat(running).isEqualTo(RunningProfile(id = "d", isDefault = true))
        assertThat(withRegistry { it.state.first() }.profiles.map { it.id }).containsExactly("d", "study").inOrder()
    }

    @Test
    fun `an active id that could leave its folder runs as Default`() {
        write("""{"profiles":[{"id":"d"},{"id":"../d"}],"defaultId":"d","activeId":"../d"}""")

        assertThat(ProfileRegistry.peek(file)).isNull()
        assertThat(withRegistry { it.running() }).isEqualTo(RunningProfile(id = "d", isDefault = true))
    }

    @Test
    fun `after a restore the app reopens in Default and keeps every profile`() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"study"}""")

        val restored = withRegistry { it.reopenInDefault() }

        assertThat(restored.activeId).isEqualTo("d")
        assertThat(restored.profiles.map { it.id }).containsExactly("d", "study").inOrder()
        assertThat(ProfileRegistry.peek(file)).isEqualTo(RunningProfile(id = "d", isDefault = true))
    }

    @Test
    fun `a restore that brought no registry starts Default`() {
        val restored = withRegistry(newId = { "fresh" }) { it.reopenInDefault() }

        assertThat(restored.defaultId).isEqualTo("fresh")
        assertThat(restored.activeId).isEqualTo("fresh")
    }

    @Test
    fun `profiles can be added up to the limit`() {
        var next = 0
        val added = withRegistry(newId = { "p${next++}" }) { registry -> (1..6).map { registry.add("P$it", "", ProfileAvatar()) } }

        assertThat(added.take(4).map { it?.name }).containsExactly("P1", "P2", "P3", "P4").inOrder()
        assertThat(added.drop(4)).containsExactly(null, null)
        assertThat(withRegistry { it.state.first() }.profiles).hasSize(ProfileRegistry.MAX_PROFILES)
    }

    @Test
    fun `only Default may be renamed to a blank name`() {
        write("""{"profiles":[{"id":"d","name":"Me"},{"id":"study","name":"Study"}],"defaultId":"d","activeId":"d"}""")

        withRegistry { registry ->
            assertThat(registry.rename("study", " ")).isFalse()
            assertThat(registry.rename("study", "Books")).isTrue()
            assertThat(registry.rename("d", "")).isTrue()
            assertThat(registry.rename("missing", "X")).isFalse()
        }

        assertThat(withRegistry { it.state.first() }.profiles.map { it.name }).containsExactly("", "Books").inOrder()
    }

    @Test
    fun `a profile's colour and avatar change together, and only for a profile that exists`() {
        write("""{"profiles":[{"id":"d"},{"id":"study","name":"Study"}],"defaultId":"d","activeId":"d"}""")
        val photo = ProfileAvatar(kind = ProfileAvatar.PHOTO, photoVersion = 7)

        withRegistry { registry ->
            assertThat(registry.setAppearance("study", "teal", photo)).isTrue()
            assertThat(registry.setAppearance("missing", "teal", photo)).isFalse()
        }

        val study = withRegistry { it.state.first() }.profiles.single { it.id == "study" }
        assertThat(study.colorKey).isEqualTo("teal")
        assertThat(study.avatar).isEqualTo(photo)
        assertThat(study.name).isEqualTo("Study")
    }

    @Test
    fun `each profile keeps the splash tone it last showed`() {
        write("""{"profiles":[{"id":"d"},{"id":"study","name":"Study"}],"defaultId":"d","activeId":"d"}""")

        withRegistry { registry ->
            registry.setSplashTone("study", "LIGHT")
            registry.setSplashTone("missing", "DARK")
        }

        val profiles = withRegistry { it.state.first() }.profiles
        assertThat(profiles.map { it.splashTone }).containsExactly("", "LIGHT").inOrder()
    }

    @Test
    fun `a TV asks who is watching until it is told not to`() {
        write("""{"profiles":[{"id":"d"}],"defaultId":"d","activeId":"d"}""")

        assertThat(withRegistry { it.state.first() }.askWhosWatching).isTrue()
        withRegistry { it.setAskWhosWatching(false) }

        assertThat(withRegistry { it.state.first() }.askWhosWatching).isFalse()
    }

    @Test
    fun `a master restore adds the profiles this device lacks, under their own ids, and never past the limit`() {
        write("""{"profiles":[{"id":"d"},{"id":"study","name":"Study here"}],"defaultId":"d","activeId":"d"}""")

        withRegistry { registry ->
            assertThat(registry.addRestored(listOf(Profile("study", name = "Study there"), Profile("gaming", name = "Gaming")))).isTrue()
            assertThat(registry.addRestored(listOf(Profile("a1"), Profile("a2"), Profile("a3")))).isFalse()
        }

        val profiles = withRegistry { it.state.first() }.profiles
        assertThat(profiles.map { it.id }).containsExactly("d", "study", "gaming").inOrder()
        assertThat(profiles.single { it.id == "study" }.name).isEqualTo("Study here")
    }

    @Test
    fun `Default and the active profile are never removed`() {
        write("""{"profiles":[{"id":"d"},{"id":"study"},{"id":"music"}],"defaultId":"d","activeId":"study"}""")

        val results = withRegistry { registry -> listOf("d", "study", "music", "missing").map { registry.remove(it) } }

        assertThat(results).containsExactly(false, false, true, false).inOrder()
        assertThat(withRegistry { it.state.first() }.profiles.map { it.id }).containsExactly("d", "study").inOrder()
    }

    @Test
    fun `a switch leaves a marker the next process clears once`() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"d"}""")

        val results =
            withRegistry { registry ->
                registry.setActive("study")
                listOf(registry.finishPostSwitch(), registry.finishPostSwitch())
            }

        assertThat(results).containsExactly(true, false).inOrder()
        assertThat(ProfileRegistry.peek(file)).isEqualTo(RunningProfile(id = "study", isDefault = false))
    }

    @Test
    fun `the first process after a switch knows it`() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"d"}""")

        withRegistry { it.setActive("study") }

        assertThat(ProfileRegistry.peek(file)).isEqualTo(RunningProfile(id = "study", isDefault = false, afterSwitch = true))
    }

    @Test
    fun `a start without a switch finds no marker`() {
        write("""{"profiles":[{"id":"d"}],"defaultId":"d","activeId":"d"}""")

        assertThat(withRegistry { it.finishPostSwitch() }).isFalse()
    }

    @Test
    fun `a file from a newer version still reads`() {
        write(
            """{"profiles":[{"id":"d","avatar":{"kind":"hologram","frames":3},"pin":"x"}],""" +
                """"defaultId":"d","activeId":"d","cloudLinked":true}""",
        )

        val state = withRegistry { it.state.first() }

        assertThat(withRegistry { it.running() }).isEqualTo(RunningProfile(id = "d", isDefault = true))
        assertThat(
            state.profiles
                .single()
                .avatar.kind,
        ).isEqualTo("hologram")
    }

    @Test
    fun `a missing Default is recreated ahead of the other profiles`() {
        val state = ProfileRegistryState(profiles = listOf(Profile(id = "study")), activeId = "study")

        val completed = state.completed(newId = { "d" }, now = { 5L })

        assertThat(completed.profiles.map { it.id }).containsExactly("d", "study").inOrder()
        assertThat(completed.defaultId).isEqualTo("d")
        assertThat(completed.activeId).isEqualTo("study")
    }

    @Test
    fun `a start reads the stored profile straight from the file`() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"study"}""")

        assertThat(ProfileRegistry.peek(file)).isEqualTo(RunningProfile(id = "study", isDefault = false))
    }

    @Test
    fun `a start leaves a missing, corrupt or incomplete file to the store`() {
        assertThat(ProfileRegistry.peek(file)).isNull()
        write("{ not json")
        assertThat(ProfileRegistry.peek(file)).isNull()
        write("""{"profiles":[{"id":"d"}],"defaultId":"d","activeId":"deleted"}""")
        assertThat(ProfileRegistry.peek(file)).isNull()
    }

    @Test
    fun `what the store writes reads back the same straight from the file`() {
        val running = withRegistry(newId = { "default-id" }) { it.running() }

        assertThat(ProfileRegistry.peek(file)).isEqualTo(running)
    }

    private fun write(json: String) {
        file.parentFile!!.mkdirs()
        file.writeText(json)
    }
}
