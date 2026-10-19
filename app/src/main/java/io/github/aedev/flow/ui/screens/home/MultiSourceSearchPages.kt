package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

/**
 * [HomeSearchPages] whose first pages ask every service the Home source filter allows, not only YouTube.
 * HomeViewModel holds this in place of the bare pages, so its search call sites stay as upstream has them;
 * the rest forwards unchanged (add a forwarder here when upstream adds a method to HomeSearchPages that it uses).
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
