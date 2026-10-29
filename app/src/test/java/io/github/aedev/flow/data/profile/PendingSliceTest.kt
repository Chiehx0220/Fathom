package io.github.aedev.flow.data.profile

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PendingSliceTest {
    private val root = Files.createTempDirectory("pending").toFile()

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a waiting slice is applied once and then removed`() =
        runBlocking<Unit> {
            PendingSlice.file(root).writeText("seed")
            val applied = mutableListOf<String>()

            PendingSlice.start(root, this) { applied += it.readText() }
            PendingSlice.await()

            assertThat(applied).containsExactly("seed")
            assertThat(root.listFiles()).isEmpty()
        }

    @Test
    fun `a slice that was being applied when the app stopped is dropped, not retried`() {
        File(root, "pending_restore.applying").writeText("crashed")

        assertThat(PendingSlice.claim(root)).isNull()
        assertThat(root.listFiles()).isEmpty()
    }

    @Test
    fun `a failing import still lets the start destination through`() =
        runBlocking<Unit> {
            PendingSlice.file(root).writeText("seed")

            PendingSlice.start(root, this) { error("unreadable") }
            PendingSlice.await()

            assertThat(PendingSlice.file(root).exists()).isFalse()
        }
}
