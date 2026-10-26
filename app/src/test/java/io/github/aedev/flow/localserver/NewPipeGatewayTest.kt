package io.github.aedev.flow.localserver

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * NewPipeExtractor on its way out: extractor calls stay in [LocalServerBackend] and [LocalServerMedia], so replacing YouTube's data source
 * touches those two files.
 */
class NewPipeGatewayTest {
    private val gateway = setOf("LocalServerBackend.kt", "LocalServerMedia.kt")

    private val extractorCalls =
        Regex(
            """NewPipe\.(getService|getServiceByUrl)\b|StreamInfo\.getInfo\b|\.get(Search|Channel|ChannelTab|Playlist|Comments)Extractor\b|\.getKioskList\b""",
        )

    @Test
    fun `extractor calls stay in the gateway files`() {
        val root = File("src/main/java/io/github/aedev/flow")
        assumeTrue("sources are not next to the test run", root.isDirectory)

        val offenders =
            listOf("localserver", "bilibili")
                .map { File(root, it) }
                .filter { it.isDirectory }
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name !in gateway }.toList() }
                .filter { file -> file.useLines { lines -> lines.any { extractorCalls.containsMatchIn(it) } } }
                .map { it.name }

        assertWithMessage("extractor calls outside ${gateway.joinToString()}").that(offenders).isEmpty()
    }
}
