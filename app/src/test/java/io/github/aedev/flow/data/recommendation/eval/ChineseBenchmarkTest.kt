/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation.eval

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.data.recommendation.eval.ChineseTitles.Source
import org.junit.Test
import java.io.File

/** Writes build/reports/neuro-benchmark/chinese.txt. */
class ChineseBenchmarkTest {
    @Test
    fun `chinese benchmark - report and regression floors`() {
        val texts = Source.entries.map(ChineseBenchmark::text)
        val learnings = ChineseBenchmark.SETUPS.map(ChineseBenchmark::learning)
        val report = ChineseBenchmark.report(texts, learnings)
        println(report)
        File("build/reports/neuro-benchmark").apply { mkdirs() }.let { File(it, "chinese.txt").writeText(report) }

        // Floors: ~0.1 under measured top-10 precision
        val top10 = learnings.associate { it.setup.label to it.top10 }
        assertThat(top10.getValue("YouTube -> YouTube")).isAtLeast(0.75)
        assertThat(top10.getValue("Bilibili -> Bilibili")).isAtLeast(0.6)
        assertThat(top10.getValue("Bilibili -> Bilibili, no partition tag")).isAtLeast(0.35)
        assertThat(top10.getValue("Bilibili -> YouTube")).isAtLeast(0.5)
        assertThat(top10.getValue("YouTube -> Bilibili")).isAtLeast(0.3)
        assertThat(top10.getValue("both -> YouTube")).isAtLeast(0.8)

        // Engine dedupes by id
        val ids = ChineseTitles.all.map { ChineseTitles.video(it, partitionTags = true).id }
        assertThat(ids).hasSize(400)
        assertThat(ids).containsNoDuplicates()
        ChineseTitles.of(Source.BILIBILI).forEach {
            assertThat(BilibiliRef.isVideoId(ChineseTitles.video(it, partitionTags = true).id)).isTrue()
        }
    }
}
