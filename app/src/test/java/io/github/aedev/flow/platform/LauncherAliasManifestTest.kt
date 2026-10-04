package io.github.aedev.flow.platform

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.util.AppIcons
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The launcher aliases live in three places: the manifest, [AppIcons] and the nightly icon overlay.
 * A drift between them can leave the app with no launcher entry after a restore or an update.
 */
class LauncherAliasManifestTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val toolsNs = "http://schemas.android.com/tools"

    private fun aliasesIn(path: String): List<Element> =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(path))
            .getElementsByTagName("activity-alias")
            .let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    // Fathom's overlay manifest removes the launcher icons it does not ship; the merged manifest has what is left.
    private val removedByOverlay: Set<String> =
        aliasesIn("src/fathom/AndroidManifest.xml")
            .filter { it.getAttributeNS(toolsNs, "node") == "remove" }
            .map { it.android("name") }
            .toSet()

    private val aliases: List<Element> = aliasesIn("src/main/AndroidManifest.xml").filterNot { it.android("name") in removedByOverlay }

    private fun Element.android(name: String) = getAttributeNS(androidNs, name)

    @Test
    fun `the manifest declares exactly the registered aliases in the same order`() {
        assertThat(aliases.map { it.android("name") }).containsExactlyElementsIn(AppIcons.ALL_SUFFIXES).inOrder()
    }

    @Test
    fun `only the default alias is enabled out of the box`() {
        val enabled = aliases.filter { it.android("enabled") != "false" }.map { it.android("name") }

        assertThat(enabled).containsExactly(AppIcons.DEFAULT_SUFFIX)
    }

    @Test
    fun `every alias icon has a nightly version`() {
        val nightlyMipmaps = File("src/nightly/res/mipmap-anydpi-v26")
        val icons = aliases.flatMap { listOf(it.android("icon"), it.android("roundIcon")) }

        val missing = icons.map { it.removePrefix("@mipmap/") }.filterNot { File(nightlyMipmaps, "$it.xml").isFile }

        assertThat(missing).isEmpty()
    }
}
