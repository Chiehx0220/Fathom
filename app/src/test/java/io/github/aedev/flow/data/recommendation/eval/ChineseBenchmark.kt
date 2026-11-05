/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.IcuWordBreaker
import io.github.aedev.flow.data.recommendation.IdfSnapshot
import io.github.aedev.flow.data.recommendation.NeuroMaintenance
import io.github.aedev.flow.data.recommendation.NeuroTokenizer
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.recommendation.eval.ChineseTitles.FAMILIES
import io.github.aedev.flow.data.recommendation.eval.ChineseTitles.Source
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark.watch
import kotlinx.coroutines.runBlocking
import java.util.Locale

/** Tokenizer key purity and engine top-10 precision on [ChineseTitles]; deterministic (no rank jitter). */
internal object ChineseBenchmark {
    private const val TOP_N = 10
    private const val MIN_DOCUMENTS = 3
    private const val LISTED = 24
    private const val IMPURE_BELOW = 0.5

    /** Top-10 precision of a random ranking over [FAMILIES]. */
    const val CHANCE = 0.2

    data class Key(
        val key: String,
        val documents: Int,
        val purity: Double,
    )

    data class TextResult(
        val source: Source,
        val coverage: Double,
        val keysPerTitle: Double,
        val purity: Double,
        val frequent: List<Key>,
        val impure: List<Key>,
    )

    /** Purity: share of a key's uses in its top family, for keys in at least [MIN_DOCUMENTS] titles. */
    fun text(source: Source): TextResult {
        val tokenizer =
            NeuroTokenizer(IcuWordBreaker).apply {
                languageProfile = "zh"
                keyLanguage = "zh"
            }
        val idf = IdfSnapshot(emptyMap(), 0)
        val titles = ChineseTitles.of(source)
        val keysOf = titles.map { tokenizer.extractFeatures(ChineseTitles.video(it, partitionTags = false), idf).topics.keys }
        val families = HashMap<String, MutableMap<String, Int>>()
        titles.zip(keysOf).forEach { (title, keys) ->
            keys.forEach { families.getOrPut(it) { HashMap() }.merge(title.family, 1, Int::plus) }
        }
        val keys =
            families.mapNotNull { (key, byFamily) ->
                val documents = byFamily.values.sum()
                if (documents < MIN_DOCUMENTS) null else Key(key, documents, byFamily.values.max().toDouble() / documents)
            }
        val uses = keys.sumOf { it.documents }
        return TextResult(
            source = source,
            coverage = keysOf.count { it.isNotEmpty() }.toDouble() / titles.size,
            keysPerTitle = keysOf.sumOf { it.size }.toDouble() / titles.size,
            purity = if (uses == 0) 0.0 else keys.sumOf { it.purity * it.documents } / uses,
            frequent = keys.sortedByDescending { it.documents }.take(LISTED),
            impure = keys.filter { it.purity < IMPURE_BELOW }.sortedByDescending { it.documents }.take(LISTED),
        )
    }

    /** Trains on one family's even rows of [watched]; ranks the odd rows of all families on [ranked]. */
    data class Setup(
        val watched: List<Source>,
        val ranked: List<Source>,
        val partitionTags: Boolean = true,
    ) {
        val label get() = "${name(watched)} -> ${name(ranked)}" + if (partitionTags) "" else ", no partition tag"

        private fun name(sources: List<Source>) = sources.singleOrNull()?.label ?: "both"
    }

    data class Learning(
        val setup: Setup,
        val top10: Double,
        val perFamily: Map<String, Double>,
    )

    private val YOUTUBE = listOf(Source.YOUTUBE)
    private val BILIBILI = listOf(Source.BILIBILI)
    private val BOTH = Source.entries

    val SETUPS =
        listOf(
            Setup(YOUTUBE, YOUTUBE),
            Setup(BILIBILI, BILIBILI),
            Setup(BILIBILI, BILIBILI, partitionTags = false),
            Setup(YOUTUBE, BILIBILI),
            Setup(YOUTUBE, BILIBILI, partitionTags = false),
            Setup(BILIBILI, YOUTUBE),
            Setup(BILIBILI, YOUTUBE, partitionTags = false),
            Setup(BOTH, YOUTUBE),
            Setup(BOTH, BILIBILI),
            Setup(BOTH, BOTH),
        )

    private val brain = UserBrain(schemaVersion = NeuroMaintenance.TARGET_SCHEMA_VERSION, languageProfile = "zh", keyLanguage = "zh")

    // JDK BreakIterator leaves Han runs unsplit, Android ICU splits them: swap in ICU4J
    private fun engine(): FlowNeuroEngine =
        NeuroLearningBenchmark.engine(brain, NeuroLearningBenchmark.NO_JITTER).also {
            FlowNeuroEngine::class.java
                .getDeclaredField("tokenizer")
                .apply { isAccessible = true }
                .set(it, NeuroTokenizer(IcuWordBreaker))
        }

    fun learning(setup: Setup): Learning {
        val candidates = setup.ranked.flatMap { source -> ChineseTitles.of(source).filterNot { it.trains } }
        val pool: List<Video> = candidates.map { ChineseTitles.video(it, setup.partitionTags) }
        val familyOf = pool.zip(candidates) { video, title -> video.id to title.family }.toMap()
        val perFamily =
            FAMILIES.associateWith { target ->
                runBlocking {
                    val engine = engine()
                    setup.watched.flatMap { ChineseTitles.of(it, target) }.filter { it.trains }.forEach {
                        engine.watch(ChineseTitles.video(it, setup.partitionTags))
                    }
                    val top = engine.rank(pool, emptySet()).take(TOP_N)
                    engine.shutdown()
                    top.count { familyOf[it.id] == target } / TOP_N.toDouble()
                }
            }
        return Learning(setup, perFamily.values.average(), perFamily)
    }

    private fun f(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    private fun List<Key>.listed() = joinToString(" ") { "${it.key}:${it.documents}/${f(it.purity)}" }

    fun report(
        texts: List<TextResult>,
        learnings: List<Learning>,
    ): String =
        buildString {
            appendLine("Chinese benchmark: ${ChineseTitles.all.size} real titles, ${FAMILIES.size} subjects, 40 per subject and service")
            appendLine()
            appendLine("TEXT (title only, no tags)")
            texts.forEach { t ->
                appendLine(
                    "${t.source.code}: coverage ${f(t.coverage)} | keys per title ${f(t.keysPerTitle)} | purity ${f(t.purity)}" +
                        " (chance 0.20+, 1.00 = every key in one subject)",
                )
                appendLine("  most used: ${t.frequent.listed()}")
                appendLine("  mixed (purity < $IMPURE_BELOW): ${t.impure.listed()}")
            }
            appendLine()
            appendLine("LEARNING (share of the viewer's subject in the top $TOP_N, chance ${f(CHANCE)}; mean of ${FAMILIES.size} subjects)")
            learnings.forEach { l ->
                appendLine(
                    "${l.setup.label.padEnd(40)} ${f(l.top10)} | " +
                        l.perFamily.entries.joinToString(" ") { "${it.key.take(4)} ${f(it.value)}" },
                )
            }
        }
}
