package io.github.aedev.flow.data.local.migrations

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * Upstream numbers its schemas below [FORK_BLOCK_START]; this fork's live in the block above it.
 * Merging an upstream schema change drops a new upstream file here, and nothing else notices if the
 * fork's own schema and migrations were not brought along. These tests do.
 */
class SchemaParityTest {
    private val schemaDir =
        listOf("schemas", "app/schemas")
            .map { File("$it/io.github.aedev.flow.data.local.AppDatabase") }
            .first { it.isDirectory }

    private val versions =
        schemaDir
            .listFiles { file -> file.extension == "json" }!!
            .associate { it.nameWithoutExtension.toInt() to it }

    private fun columnsByTable(version: Int): Map<String, Set<String>> =
        Json
            .parseToJsonElement(versions.getValue(version).readText())
            .jsonObject
            .getValue("database")
            .jsonObject
            .getValue("entities")
            .jsonArray
            .associate { entity ->
                val table = entity.jsonObject
                table.getValue("tableName").jsonPrimitive.content to
                    table.getValue("fields").jsonArray.mapTo(HashSet()) {
                        it.jsonObject
                            .getValue("columnName")
                            .jsonPrimitive.content
                    }
            }

    @Test
    fun `the fork's newest schema holds every table and column of upstream's newest`() {
        val upstream = columnsByTable(versions.keys.filter { it < FORK_BLOCK_START }.max())
        val fork = columnsByTable(versions.keys.filter { it >= FORK_BLOCK_START }.max())

        val missing =
            upstream.flatMap { (table, columns) ->
                val have = fork[table] ?: return@flatMap listOf("table $table")
                (columns - have).map { "$table.$it" }
            }
        assertThat(missing).isEmpty()
    }

    @Test
    fun `migrations lead from upstream's last shared version to the fork's newest`() {
        val forkLast = versions.keys.filter { it >= FORK_BLOCK_START }.max()
        val edges = MIGRATIONS.groupBy({ it.startVersion }, { it.endVersion })
        val reachable = HashSet<Int>()
        val queue = ArrayDeque(listOf(FORK_ENTRY_VERSION))
        while (queue.isNotEmpty()) {
            val version = queue.removeFirst()
            if (reachable.add(version)) queue.addAll(edges[version].orEmpty())
        }

        assertThat(reachable).contains(forkLast)
    }

    private companion object {
        const val FORK_BLOCK_START = 10_000
        const val FORK_ENTRY_VERSION = 28
    }
}
