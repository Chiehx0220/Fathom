package io.github.aedev.flow.localserver

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * [buildHomeFeedLanes] and [assembleHomeFeed] defaults hide new upstream parameters from the compiler;
 * every parameter is passed by [buildAndRankHomeFeed] or listed here as omitted on purpose.
 */
class HomeLanesMirrorTest {
    private val assembly = File("src/main/java/io/github/aedev/flow/ui/screens/home/HomeFeedAssembly.kt").readText()
    private val mirror = File("src/main/java/io/github/aedev/flow/localserver/LocalServerHomeFeed.kt").readText()

    @Test
    fun `lane builder takes every upstream parameter or omits it on purpose`() {
        assertCovered("buildHomeFeedLanes", omitted = setOf("rawNeighbours", "searchOf"))
    }

    @Test
    fun `feed assembly takes every upstream parameter or omits it on purpose`() {
        assertCovered("assembleHomeFeed", omitted = setOf("targetSize"))
    }

    private fun assertCovered(
        function: String,
        omitted: Set<String>,
    ) {
        val declared = parameterNames(function)
        val passed = argumentNames(function)
        assertEquals("$function: arguments the declaration does not have", emptySet<String>(), passed - declared)
        assertEquals("$function: omitted list names parameters that no longer exist", emptySet<String>(), omitted - declared)
        assertEquals(
            "$function: new upstream parameters; pass them in LocalServerHomeFeed.kt or list them as omitted here",
            omitted,
            declared - passed,
        )
    }

    private fun parameterNames(function: String): Set<String> {
        val start = assembly.indexOf("fun $function(")
        check(start >= 0) { "fun $function not found in HomeFeedAssembly.kt" }
        val body = enclosed(assembly, assembly.indexOf('(', start))
        return topLevelParts(body).mapNotNull { NAME_COLON.find(it)?.groupValues?.get(1) }.toSet()
    }

    private fun argumentNames(function: String): Set<String> {
        val start = mirror.indexOf("$function(")
        check(start >= 0) { "call $function not found in LocalServerHomeFeed.kt" }
        val body = enclosed(mirror, mirror.indexOf('(', start))
        return topLevelParts(body).mapNotNull { NAME_EQUALS.find(it)?.groupValues?.get(1) }.toSet()
    }

    /** Text between the parenthesis at [open] and its match. */
    private fun enclosed(
        text: String,
        open: Int,
    ): String {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (--depth == 0) return text.substring(open + 1, i)
            }
        }
        error("unbalanced parentheses")
    }

    private fun topLevelParts(body: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var from = 0
        body.forEachIndexed { i, c ->
            when (c) {
                '(', '[', '{' -> {
                    depth++
                }

                ')', ']', '}' -> {
                    depth--
                }

                ',' -> {
                    if (depth == 0) {
                        parts += body.substring(from, i)
                        from = i + 1
                    }
                }
            }
        }
        parts += body.substring(from)
        return parts.filter { it.isNotBlank() }
    }

    private companion object {
        val NAME_COLON = Regex("""^\s*(\w+)\s*:""")
        val NAME_EQUALS = Regex("""^\s*(\w+)\s*=""")
    }
}
