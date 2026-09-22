package io.github.aedev.flow.localserver

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page

/** Listing pages: the home feed, search results, history, watch later, and the library (subscriptions, playlists). */
object HtmlRendererListings {
    /** The home page frame; its feed is fetched as a fragment (see [renderHomeFeed]) so the page appears at once. */
    @JvmStatic
    fun renderHomeSkeleton(
        serviceId: Int,
        isTv: Boolean,
    ): String {
        val body =
            WebShell.header(serviceId, "") +
                "<main class=\"container\">\n" +
                WebUi.asyncRegion("/?feed=ajax&serviceId=$serviceId", "Loading Home Feed...") +
                "</main>\n"
        return WebShell.page("${HtmlRendererCommon.getServiceName(serviceId)} - Fathom", body, isTv)
    }

    /** One batch of the home feed, ending with a "Load more" that fetches the next batch when there is one. */
    @JvmStatic
    fun renderHomeFeed(
        serviceId: Int,
        items: List<InfoItem>,
        nextToken: String?,
    ): String =
        WebUi.grid(serviceId, items) +
            WebUi.loadMore(nextToken?.takeIf { it.isNotEmpty() }?.let { "/?feed=ajax&serviceId=$serviceId&nextPage=${WebUi.enc(it)}" })

    @JvmStatic
    fun renderHistory(
        serviceId: Int,
        items: List<InfoItem>?,
        isTv: Boolean,
    ): String {
        val sb = StringBuilder(WebShell.header(serviceId, "", "history"))
        sb.append("<main class=\"container\">\n")
        if (items.isNullOrEmpty()) {
            sb.append(WebUi.pageTitle("history", "Watch History"))
              .append(WebUi.notice("Your watch history is empty. Start watching videos to see them here!"))
        } else {
            sb.append(WebUi.pageTitle("history", "Watch History", WebUi.button("Select", attrs = "data-action=\"history-select-mode\"")))
              .append("<div id=\"history-select-bar\" class=\"select-bar\" hidden>\n")
              .append("  <label class=\"select-all\"><input type=\"checkbox\" id=\"history-select-all\"> Select all</label>\n")
              .append("  <span id=\"history-select-count\" class=\"select-count\">0 selected</span>\n")
              .append("  <div class=\"select-actions\">")
              .append(WebUi.button("Delete selected", attrs = "data-action=\"history-delete-selected\" data-service=\"$serviceId\""))
              .append(WebUi.button("Cancel", attrs = "data-action=\"history-select-mode\""))
              .append("</div>\n</div>\n")
              .append(WebUi.grid(serviceId, items, removable = true))
        }
        sb.append("</main>\n")
        return WebShell.page("Watch History - Fathom", sb.toString(), isTv)
    }

    @JvmStatic
    fun renderWatchLater(
        serviceId: Int,
        items: List<InfoItem>?,
        isTv: Boolean,
    ): String {
        val sb = StringBuilder(WebShell.header(serviceId, "", "watch-later"))
        sb.append("<main class=\"container\">\n").append(WebUi.pageTitle("schedule", "Watch Later"))
        if (items.isNullOrEmpty()) {
            sb.append(WebUi.notice("No videos in Watch Later. Browse videos and press \"Watch Later\" to add them."))
        } else {
            sb.append(WebUi.grid(serviceId, items))
        }
        sb.append("</main>\n")
        return WebShell.page("Watch Later - Fathom", sb.toString(), isTv)
    }

    @JvmStatic
    fun renderSearch(
        serviceId: Int,
        query: String,
        items: List<InfoItem>,
        nextPage: Page?,
        isTv: Boolean,
    ): String {
        val body =
            WebShell.header(serviceId, query) +
                "<main class=\"container\">\n" +
                WebUi.pageTitle("search", "Results for: $query") +
                renderSearchResultsFragment(serviceId, query, items, nextPage) +
                "</main>\n"
        return WebShell.page("Search: $query", body, isTv)
    }

    /** A batch of search results with its "Load more"; the same fragment the first page and every follow-up use. */
    @JvmStatic
    fun renderSearchResultsFragment(
        serviceId: Int,
        query: String,
        items: List<InfoItem>,
        nextPage: Page?,
    ): String {
        val next =
            HtmlRendererCommon.serializePage(nextPage)?.let {
                "/search?ajax=1&serviceId=$serviceId&q=${WebUi.enc(query)}&nextPage=${WebUi.enc(it)}"
            }
        return WebUi.grid(serviceId, items) + WebUi.loadMore(next)
    }

    /** The library: feed of subscribed channels, the channels themselves, saved playlists, watch later. */
    @JvmStatic
    fun renderSubscriptions(
        serviceId: Int,
        channels: List<InfoItem>?,
        playlists: List<InfoItem>?,
        watchLater: List<InfoItem>?,
        activeTab: String,
        isTv: Boolean,
    ): String {
        val tab = if (activeTab in setOf("channels", "playlists", "watch_later")) activeTab else "feed"
        fun tabLink(
            key: String,
            iconName: String,
            label: String,
            count: Int?,
        ): String {
            val text = if (count == null) label else "$label ($count)"
            val active = if (key == tab) " active" else ""
            return "<a class=\"subs-tab$active\" href=\"/subscriptions?serviceId=$serviceId&tab=$key\">${WebUi.icon(iconName)}$text</a>\n"
        }

        val sb = StringBuilder(WebShell.header(serviceId, "", "subscriptions"))
        sb.append("<main class=\"container\">\n<div class=\"subs-tabbar\" role=\"tablist\">\n")
          .append(tabLink("feed", "dynamic_feed", "Feed", null))
          .append(tabLink("channels", "person", "Channels", channels?.size ?: 0))
          .append(tabLink("playlists", "star", "Playlists", playlists?.size ?: 0))
          .append(tabLink("watch_later", "schedule", "Watch Later", watchLater?.size ?: 0))
          .append("</div>\n")

        val (list, empty) =
            when (tab) {
                "playlists" -> playlists to "You haven't saved any playlists yet."
                "watch_later" -> watchLater to "Your Watch Later list is empty."
                else -> channels to "You haven't subscribed to any channels yet."
            }
        when {
            // The feed asks every subscribed channel for its latest uploads, which can take seconds, so it loads after the frame.
            tab == "feed" && !channels.isNullOrEmpty() ->
                sb.append(WebUi.asyncRegion("/subscriptions?tab=feed&feed=ajax&serviceId=$serviceId", "Loading latest uploads..."))
            list.isNullOrEmpty() -> sb.append(WebUi.notice(empty))
            else -> sb.append(WebUi.grid(serviceId, list))
        }
        sb.append("</main>\n")
        return WebShell.page("Library - Fathom", sb.toString(), isTv)
    }
}
