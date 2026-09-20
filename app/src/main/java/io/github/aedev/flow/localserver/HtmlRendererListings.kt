package org.schabi.newpipe.localserver

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page

// Listing-style pages (home feed, search results, history, watch-later, subscriptions/bookmarked
// playlists library), split out of the former monolithic HtmlRenderer.java.
object HtmlRendererListings {

    @JvmStatic
    fun renderHomeSkeleton(serviceId: Int, isTv: Boolean): String {
        val sb = StringBuilder()
        sb.append(HtmlRendererCommon.getHeaderHtml(serviceId, ""))
        sb.append("<div class=\"container\">\n")
          .append("  <div id=\"home-feed-loader\" style=\"text-align: center; padding: 100px 0;\">\n")
          .append("    <div style=\"display: inline-block; width: 50px; height: 50px; border: 4px solid var(--search-input-bg); border-top: 4px solid var(--logo-color); border-radius: 50%; animation: spin 0.8s linear infinite;\"></div>\n")
          .append("    <div style=\"margin-top: 24px; font-size: 16px; font-weight: 500; color: var(--text-color);\">Loading Home Feed...</div>\n")
          .append("  </div>\n")
          // padding-top set once here, not per-batch, so "Load More" appends don't each add a gap.
          .append("  <div id=\"home-feed-content\" style=\"display: none; padding-top: 20px;\"></div>\n")
          .append("</div>\n")
          .append("<style>\n")
          .append("  @keyframes spin {\n")
          .append("    0% { transform: rotate(0deg); }\n")
          .append("    100% { transform: rotate(360deg); }\n")
          .append("  }\n")
          .append("</style>\n")
          .append("<script>\n")
          // Replaces the trailing .pagination wrapper with {new grid + new wrapper}, in place -
          // same technique as loadMoreComments(). fetchText routes a failed batch to .catch()
          // below instead of rendering it as the next page.
          .append("  window.loadMoreHome = function(btn, token, svcId) {\n")
          .append("      const wrapper = btn.parentElement;\n")
          .append("      btn.textContent = 'Loading...';\n")
          .append("      btn.style.pointerEvents = 'none';\n")
          .append("      fetchText('/?feed=ajax&serviceId=' + svcId + '&nextPage=' + encodeURIComponent(token),\n")
          .append("          html => { if (wrapper) wrapper.outerHTML = html; },\n")
          .append("          () => { btn.textContent = 'Failed to load. Tap to retry'; btn.style.pointerEvents = 'auto'; });\n")
          .append("  };\n")
          .append("  document.addEventListener('DOMContentLoaded', () => {\n")
          .append("      const loader = document.getElementById('home-feed-loader');\n")
          .append("      const content = document.getElementById('home-feed-content');\n")
          .append("      fetchText('/?feed=ajax&serviceId=' + $serviceId,\n")
          .append("          html => {\n")
          .append("              if (content) {\n")
          .append("                  content.innerHTML = html;\n")
          .append("                  content.style.display = 'block';\n")
          .append("              }\n")
          .append("              if (loader) loader.style.display = 'none';\n")
          .append("          },\n")
          .append("          err => {\n")
          .append("              if (loader) loader.innerHTML = '<div class=\"loading-placeholder\" style=\"color: #ff4b5c; border-color: rgba(255, 75, 92, 0.2);\">Failed to load home feed: ' + err.message + '</div>';\n")
          .append("          });\n")
          .append("  });\n")
          .append("</script>\n")
        return HtmlRendererCommon.wrapInTemplate(HtmlRendererCommon.getServiceName(serviceId) + " - Fathom", sb.toString(), isTv)
    }

    @JvmStatic
    fun renderHomeFeed(serviceId: Int, items: List<InfoItem>, nextToken: String?): String {
        val sb = StringBuilder()
        HtmlRendererCommon.renderGrid(sb, serviceId, items)

        // Always emits the .pagination wrapper (populated or empty) - loadMoreHome() needs it
        // present in every response to stay replaceable.
        sb.append("  <div class=\"pagination\">\n")
        if (!nextToken.isNullOrEmpty()) {
            val tokenJs = HtmlRendererCommon.escapeJs(nextToken)
            sb.append("    <a href=\"#\" class=\"btn-page\" onclick=\"loadMoreHome(this, '$tokenJs', $serviceId); return false;\">Load More</a>\n")
        }
        sb.append("  </div>\n")
        return sb.toString()
    }

    @JvmStatic
    fun renderHistory(serviceId: Int, items: List<InfoItem>?, isTv: Boolean): String {
        val sb = StringBuilder()
        sb.append(HtmlRendererCommon.getHeaderHtml(serviceId, "", "history"))
        sb.append("<div class=\"container\">\n")

        if (items == null || items.isEmpty()) {
            sb.append("  <h2 style=\"margin-bottom: 20px; font-weight: 700;\"><span class=\"material-symbols-rounded\" style=\"font-size:22px; vertical-align:-4px; margin-right:6px;\">history</span>Watch History</h2>\n")
              .append("<div class=\"loading-placeholder\">Your watch history is empty. Start watching videos to see them here!</div>\n")
        } else {
            sb.append("  <div class=\"page-header-row\">\n")
              .append("    <h2 style=\"font-weight: 700;\"><span class=\"material-symbols-rounded\" style=\"font-size:22px; vertical-align:-4px; margin-right:6px;\">history</span>Watch History</h2>\n")
              .append("    <button type=\"button\" class=\"btn-page\" onclick=\"toggleHistorySelectMode()\">Select</button>\n")
              .append("  </div>\n")
              .append("  <div id=\"history-select-bar\" class=\"history-select-bar\" style=\"display:none;\">\n")
              .append("    <div class=\"history-select-info\">\n")
              .append("      <label class=\"history-select-all-label\"><input type=\"checkbox\" id=\"history-select-all\" onchange=\"toggleSelectAllHistory(this)\"> Select All</label>\n")
              .append("      <span id=\"history-select-count\" class=\"history-select-count\">0 selected</span>\n")
              .append("    </div>\n")
              .append("    <div class=\"history-select-actions\">\n")
              .append("      <button type=\"button\" class=\"btn-page\" onclick=\"deleteSelectedHistory($serviceId)\">Delete Selected</button>\n")
              .append("      <button type=\"button\" class=\"btn-page\" onclick=\"toggleHistorySelectMode()\">Cancel</button>\n")
              .append("    </div>\n")
              .append("  </div>\n")
            HtmlRendererCommon.renderGrid(sb, serviceId, items, true)
        }

        sb.append("</div>\n")
        return HtmlRendererCommon.wrapInTemplate("Watch History - Fathom", sb.toString(), isTv)
    }

    @JvmStatic
    fun renderWatchLater(serviceId: Int, items: List<InfoItem>?, isTv: Boolean): String {
        val sb = StringBuilder()
        sb.append(HtmlRendererCommon.getHeaderHtml(serviceId, "", "watch-later"))
        sb.append("<div class=\"container\">\n")
          .append("  <h2 style=\"margin-bottom: 20px; font-weight: 700;\">⭐ Watch Later</h2>\n")

        if (items == null || items.isEmpty()) {
            sb.append("<div class=\"loading-placeholder\">No videos in Watch Later list. Browse videos and click \"Watch Later\" to add them!</div>\n")
        } else {
            HtmlRendererCommon.renderGrid(sb, serviceId, items)
        }

        sb.append("</div>\n")
        return HtmlRendererCommon.wrapInTemplate("Watch Later - Fathom", sb.toString(), isTv)
    }

    @JvmStatic
    fun renderSearch(serviceId: Int, query: String, items: List<InfoItem>, nextPage: Page?, isTv: Boolean): String {
        val sb = StringBuilder()
        sb.append(HtmlRendererCommon.getHeaderHtml(serviceId, query))
        val queryEscaped = HtmlRendererCommon.escapeHtml(query)
        sb.append("<div class=\"container\">\n")
          .append("  <h2 style=\"margin-bottom: 20px; font-weight: 700;\">🔍 Search Results for: $queryEscaped</h2>\n")
          .append(renderSearchResultsFragment(serviceId, query, items, nextPage))
          .append("</div>\n")
        return HtmlRendererCommon.wrapInTemplate("Search: $query", sb.toString(), isTv)
    }

    // Grid + "Load More" for a search-results batch - shared by renderSearch() and loadMoreSearch()'s
    // ajax=1 follow-ups, same split as renderHomeFeed()/renderHomeSkeleton().
    @JvmStatic
    fun renderSearchResultsFragment(serviceId: Int, query: String, items: List<InfoItem>, nextPage: Page?): String {
        val sb = StringBuilder()
        HtmlRendererCommon.renderGrid(sb, serviceId, items)

        val nextPageJs = HtmlRendererCommon.serializePageJs(nextPage)
        if (nextPageJs != null) {
            val queryJs = HtmlRendererCommon.escapeJs(query)
            sb.append("  <div class=\"pagination\">\n")
              .append("    <a href=\"#\" class=\"btn-page\" onclick=\"loadMoreSearch(this, '$nextPageJs', $serviceId, '$queryJs'); return false;\">Load More</a>\n")
              .append("  </div>\n")
        }
        return sb.toString()
    }

    @JvmStatic
    fun renderSubscriptions(serviceId: Int, channels: List<InfoItem>?, playlists: List<InfoItem>?, watchLater: List<InfoItem>?, activeTab: String, isTv: Boolean): String {
        val sb = StringBuilder()
        sb.append(HtmlRendererCommon.getHeaderHtml(serviceId, "", "subscriptions"))
        sb.append("<div class=\"container\">\n")

        val isPlaylists = activeTab == "playlists"
        val isWatchLater = activeTab == "watch_later"
        val isChannels = activeTab == "channels"
        val isFeed = !isPlaylists && !isWatchLater && !isChannels

        val feedClass = if (isFeed) "active" else ""
        val channelsClass = if (isChannels) "active" else ""
        val playlistsClass = if (isPlaylists) "active" else ""
        val watchLaterClass = if (isWatchLater) "active" else ""

        val channelsCount = channels?.size ?: 0
        val playlistsCount = playlists?.size ?: 0
        val watchLaterCount = watchLater?.size ?: 0

        sb.append("  <div class=\"subs-tabbar\">\n")
          .append("    <a href=\"/subscriptions?serviceId=$serviceId&tab=feed\" class=\"subs-tab $feedClass\"><span class=\"material-symbols-rounded\">dynamic_feed</span>Feed</a>\n")
          .append("    <a href=\"/subscriptions?serviceId=$serviceId&tab=channels\" class=\"subs-tab $channelsClass\"><span class=\"material-symbols-rounded\">person</span>Channels ($channelsCount)</a>\n")
          .append("    <a href=\"/subscriptions?serviceId=$serviceId&tab=playlists\" class=\"subs-tab $playlistsClass\"><span class=\"material-symbols-rounded\">star</span>Playlists ($playlistsCount)</a>\n")
          .append("    <a href=\"/subscriptions?serviceId=$serviceId&tab=watch_later\" class=\"subs-tab $watchLaterClass\"><span class=\"material-symbols-rounded\">schedule</span>Watch Later ($watchLaterCount)</a>\n")
          .append("  </div>\n")

        if (isFeed) {
            if (channels == null || channels.isEmpty()) {
                sb.append("<div class=\"loading-placeholder\">You haven't subscribed to any channels yet.</div>\n")
            } else {
                // fetchSubscriptionFeed() is network-bound and can take seconds - loaded async
                // after the tab bar renders, mirroring renderHomeSkeleton.
                sb.append("  <div id=\"subs-feed-loader\" style=\"text-align: center; padding: 100px 0;\">\n")
                  .append("    <div style=\"display: inline-block; width: 50px; height: 50px; border: 4px solid var(--search-input-bg); border-top: 4px solid var(--logo-color); border-radius: 50%; animation: subs-feed-spin 0.8s linear infinite;\"></div>\n")
                  .append("    <div style=\"margin-top: 24px; font-size: 16px; font-weight: 500; color: var(--text-color);\">Loading latest uploads...</div>\n")
                  .append("  </div>\n")
                  .append("  <div id=\"subs-feed-content\" style=\"display: none;\"></div>\n")
                  .append("  <style>\n")
                  .append("    @keyframes subs-feed-spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }\n")
                  .append("  </style>\n")
                  .append("  <script>\n")
                  // fetchText lives in /static/script.js, which loads after this tag.
                  .append("  document.addEventListener('DOMContentLoaded', () => {\n")
                  .append("    fetchText('/subscriptions?tab=feed&feed=ajax&serviceId=$serviceId',\n")
                  .append("        html => {\n")
                  .append("            const loader = document.getElementById('subs-feed-loader');\n")
                  .append("            const content = document.getElementById('subs-feed-content');\n")
                  .append("            if (content) { content.innerHTML = html; content.style.display = 'block'; }\n")
                  .append("            if (loader) loader.style.display = 'none';\n")
                  .append("        },\n")
                  .append("        err => {\n")
                  .append("            const loader = document.getElementById('subs-feed-loader');\n")
                  .append("            if (loader) loader.innerHTML = '<div class=\"loading-placeholder\" style=\"color: #ff4b5c;\">Failed to load feed: ' + err.message + '</div>';\n")
                  .append("        });\n")
                  .append("  });\n")
                  .append("  </script>\n")
            }
        } else if (isPlaylists) {
            if (playlists == null || playlists.isEmpty()) {
                sb.append("<div class=\"loading-placeholder\">You haven't saved any playlists yet.</div>\n")
            } else {
                HtmlRendererCommon.renderGrid(sb, serviceId, playlists)
            }
        } else if (isWatchLater) {
            if (watchLater == null || watchLater.isEmpty()) {
                sb.append("<div class=\"loading-placeholder\">Your Watch Later list is empty.</div>\n")
            } else {
                HtmlRendererCommon.renderGrid(sb, serviceId, watchLater)
            }
        } else {
            if (channels == null || channels.isEmpty()) {
                sb.append("<div class=\"loading-placeholder\">You haven't subscribed to any channels yet.</div>\n")
            } else {
                HtmlRendererCommon.renderGrid(sb, serviceId, channels)
            }
        }

        sb.append("</div>\n")
        return HtmlRendererCommon.wrapInTemplate("Library - Fathom", sb.toString(), isTv)
    }
}
