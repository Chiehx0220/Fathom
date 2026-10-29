package io.github.aedev.flow.data.profile

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SwitchIntentQueueTest {
    private val dir = Files.createTempDirectory("held").toFile()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `a held playlist is found by the token it was saved under`() {
        File(dir, "3f1c2a9e-7b1d-4c6e-9a0f-2d5b8e4c1a77").writeText("{}")

        assertThat(SwitchIntentQueue.heldFile(dir, "3f1c2a9e-7b1d-4c6e-9a0f-2d5b8e4c1a77")).isNotNull()
    }

    @Test
    fun `an outside intent can't point the import at any other file`() {
        File(dir.parentFile, "secret").writeText("x")

        assertThat(SwitchIntentQueue.heldFile(dir, "../secret")).isNull()
        assertThat(SwitchIntentQueue.heldFile(dir, "missing")).isNull()
        assertThat(SwitchIntentQueue.heldFile(dir, null)).isNull()
        assertThat(SwitchIntentQueue.heldFile(dir, "")).isNull()
        File(dir.parentFile, "secret").delete()
    }
}
