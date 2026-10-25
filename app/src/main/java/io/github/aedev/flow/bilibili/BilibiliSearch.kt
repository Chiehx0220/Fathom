/*
 * Ported from PipePipeExtractor (GPL-3.0), commit aef9726d5b1172213066f60bc338eb4278651d61:
 * services/bilibili/extractors/BilibiliSearchExtractor.java.
 * Copyright the PipePipeExtractor / NewPipeExtractor contributors.
 */
package io.github.aedev.flow.bilibili

import kotlinx.serialization.json.Json
import java.net.URLEncoder

/** Search endpoint: videos, uploaders, live rooms. */
internal class BilibiliSearch(
    private val session: BilibiliSession,
    private val json: Json,
) {
    /** 1-based page; anime/films skipped. */
    suspend fun search(
        keyword: String,
        type: BilibiliSearchType,
        page: Int,
    ): BilibiliSearchPage {
        val headers = session.headers("https://www.bilibili.com/")
        val encoded = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
        val url = "$SEARCH_URL?search_type=${type.apiValue}&keyword=$encoded&page=$page"
        val response = json.decodeFromString<SearchResponse>(session.get(url, headers))
        if (response.code != 0) throw BilibiliContentNotAvailableException("Bilibili search code ${response.code}")
        val data = response.data ?: return BilibiliSearchPage(emptyList(), false)
        val items = data.result.orEmpty().mapNotNull { BilibiliSearchParser.toItem(it) }
        return BilibiliSearchPage(items, hasMore = page < data.numPages && !data.result.isNullOrEmpty())
    }

    private companion object {
        const val SEARCH_URL = "https://api.bilibili.com/x/web-interface/search/type"
    }
}
