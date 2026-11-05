/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.model.Video
import java.io.File

/** zh_titles.tsv fixture: zh-TW YouTube and Simplified Bilibili titles over five subjects. */
internal object ChineseTitles {
    val FAMILIES = listOf("TECH", "COOKING", "GAMING", "MUSIC", "KNOWLEDGE")

    enum class Source(
        val code: String,
        val label: String,
    ) {
        YOUTUBE("yt", "YouTube"),
        BILIBILI("bili", "Bilibili"),
    }

    /** [index]: row per (source, family); even trains, odd ranks. */
    data class Title(
        val source: Source,
        val family: String,
        val partition: String,
        val text: String,
        val index: Int,
    ) {
        val trains get() = index % 2 == 0
    }

    val all: List<Title> by lazy {
        val rows = HashMap<Pair<Source, String>, Int>()
        File("src/test/resources/neuro/zh_titles.tsv")
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val (code, family, partition, text) = line.split('\t')
                val source = Source.entries.first { it.code == code }
                Title(source, family, partition, text, rows.merge(source to family, 1, Int::plus)!! - 1)
            }
    }

    fun of(
        source: Source,
        family: String? = null,
    ) = all.filter { it.source == source && (family == null || it.family == family) }

    private val Title.number get() = FAMILIES.indexOf(family) * 1_000 + index

    /** Bilibili: partition tag as `BilibiliVideoMapper.tagsOf`; YouTube: bare title. */
    fun video(
        title: Title,
        partitionTags: Boolean,
    ): Video {
        val bilibili = title.source == Source.BILIBILI
        return Video(
            id = if (bilibili) "BV1${title.number.toString().padStart(9, '0')}?p=1" else "yt-${title.family.lowercase()}${title.index}",
            title = title.text,
            channelName = "channel ${title.number}",
            channelId = if (bilibili) (100_000 + title.number).toString() else "UC-${title.number}",
            thumbnailUrl = "",
            duration = 600,
            viewCount = 400_000,
            uploadDate = "3 days ago",
            tags = if (bilibili && partitionTags) listOf(title.partition) else emptyList(),
        )
    }
}
