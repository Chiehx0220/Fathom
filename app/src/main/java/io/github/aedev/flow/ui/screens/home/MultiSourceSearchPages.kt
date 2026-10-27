package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

/**
 * [HomeSearchPages] querying every service the source filter allows; HomeViewModel holds it instead of bare pages; add forwarder when
 * upstream adds a used method.
 */
internal class MultiSourceSearchPages(
    private val pages: HomeSearchPages,
    private val feedSources: HomeFeedSources,
) {
    suspend fun firstPage(query: String): List<Video> = feedSources.searchDiscovery(query, pages::firstPage)

    suspend fun nextPageOfProductive(origins: Map<String, HomeCardOrigin>) = pages.nextPageOfProductive(origins)

    suspend fun nextPageOfAny() = pages.nextPageOfAny()

    suspend fun queryOf(videoId: String) = pages.queryOf(videoId)

    suspend fun reset() = pages.reset()
}
