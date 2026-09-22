package io.github.aedev.flow.localserver

/**
 * The frame every page sits in: the document head, the top bar, the side rail (a bottom bar on phones), the "remote
 * connected" banner and the virtual pointer. Pages hand over their body and get a complete document back.
 */
object WebShell {
    private class NavItem(
        val tab: String,
        val icon: String,
        val label: String,
        val onPhone: Boolean,
        val href: (Int) -> String,
    )

    // The one list both the rail and the bottom bar are drawn from. The phone bar has less room, so History is left out of it.
    private val NAV =
        listOf(
            NavItem("youtube", "home", "Home", true) { "/?serviceId=$it" },
            NavItem("subscriptions", "subscriptions", "Subscriptions", true) { "/subscriptions?serviceId=$it" },
            NavItem("history", "history", "History", false) { "/history?serviceId=$it" },
            NavItem("settings", "settings", "Settings", true) { "/settings?serviceId=$it" },
        )

    // App-icon mark: badge, wave, depth ruler, buoy. Colours follow theme via CSS variables; the
    // wave tint is fixed, matching the launcher icon.
    private const val LOGO_SVG =
        "<svg class=\"logo-mark\" viewBox=\"0 0 100 100\" width=\"28\" height=\"28\" aria-hidden=\"true\"><defs><clipPath id=\"logo-fw\"><rect width=\"100\" height=\"100\" rx=\"18\"/></clipPath></defs>" +
            "<rect width=\"100\" height=\"100\" rx=\"18\" fill=\"var(--logo-badge-bg)\"/>" +
            "<path clip-path=\"url(#logo-fw)\" fill=\"#5B8DEF\" fill-opacity=\"0.4\" d=\"M0,58 Q6.25,53 12.5,58 T25,58 T37.5,58 T50,58 T62.5,58 T75,58 T87.5,58 T100,58 L100,100 L0,100 Z\"/>" +
            "<g stroke=\"var(--logo-badge-fg)\" stroke-width=\"5\" stroke-linecap=\"round\"><line x1=\"37\" y1=\"18\" x2=\"37\" y2=\"78\"/><line x1=\"37\" y1=\"22\" x2=\"76\" y2=\"22\"/>" +
            "<line x1=\"37\" y1=\"42\" x2=\"50\" y2=\"42\"/><line x1=\"37\" y1=\"62\" x2=\"50\" y2=\"62\"/></g><polygon points=\"58,42 58,62 75,52\" fill=\"var(--logo-badge-accent)\"/></svg>"

    // Light/dark favicon variants: a favicon cannot read the page's CSS variables.
    private const val FAVICON_LIGHT =
        "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxMDAgMTAwIj48ZGVmcz48Y2xpcFBhdGggaWQ9ImZ3Ij48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgcng9IjE4Ii8+PC9jbGlwUGF0aD48L2RlZnM+PHJlY3Qgd2lkdGg9IjEwMCIgaGVpZ2h0PSIxMDAiIHJ4PSIxOCIgZmlsbD0iI0YyRURFMCIvPjxwYXRoIGNsaXAtcGF0aD0idXJsKCNmdykiIGZpbGw9IiM1QjhERUYiIGZpbGwtb3BhY2l0eT0iMC40IiBkPSJNMCw1OCBRNi4yNSw1MyAxMi41LDU4IFQyNSw1OCBUMzcuNSw1OCBUNTAsNTggVDYyLjUsNTggVDc1LDU4IFQ4Ny41LDU4IFQxMDAsNTggTDEwMCwxMDAgTDAsMTAwIFoiLz48ZyBzdHJva2U9IiMwRjFEMzMiIHN0cm9rZS13aWR0aD0iNSIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48bGluZSB4MT0iMzciIHkxPSIxOCIgeDI9IjM3IiB5Mj0iNzgiLz48bGluZSB4MT0iMzciIHkxPSIyMiIgeDI9Ijc2IiB5Mj0iMjIiLz48bGluZSB4MT0iMzciIHkxPSI0MiIgeDI9IjUwIiB5Mj0iNDIiLz48bGluZSB4MT0iMzciIHkxPSI2MiIgeDI9IjUwIiB5Mj0iNjIiLz48L2c+PHBvbHlnb24gcG9pbnRzPSI1OCw0MiA1OCw2MiA3NSw1MiIgZmlsbD0iI0M5OUEzNCIvPjwvc3ZnPg=="
    private const val FAVICON_DARK =
        "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxMDAgMTAwIj48ZGVmcz48Y2xpcFBhdGggaWQ9ImZ3Ij48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgcng9IjE4Ii8+PC9jbGlwUGF0aD48L2RlZnM+PHJlY3Qgd2lkdGg9IjEwMCIgaGVpZ2h0PSIxMDAiIHJ4PSIxOCIgZmlsbD0iIzBGMUQzMyIvPjxwYXRoIGNsaXAtcGF0aD0idXJsKCNmdykiIGZpbGw9IiM1QjhERUYiIGZpbGwtb3BhY2l0eT0iMC40IiBkPSJNMCw1OCBRNi4yNSw1MyAxMi41LDU4IFQyNSw1OCBUMzcuNSw1OCBUNTAsNTggVDYyLjUsNTggVDc1LDU4IFQ4Ny41LDU4IFQxMDAsNTggTDEwMCwxMDAgTDAsMTAwIFoiLz48ZyBzdHJva2U9IiNGMkVERTAiIHN0cm9rZS13aWR0aD0iNSIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIj48bGluZSB4MT0iMzciIHkxPSIxOCIgeDI9IjM3IiB5Mj0iNzgiLz48bGluZSB4MT0iMzciIHkxPSIyMiIgeDI9Ijc2IiB5Mj0iMjIiLz48bGluZSB4MT0iMzciIHkxPSI0MiIgeDI9IjUwIiB5Mj0iNDIiLz48bGluZSB4MT0iMzciIHkxPSI2MiIgeDI9IjUwIiB5Mj0iNjIiLz48L2c+PHBvbHlnb24gcG9pbnRzPSI1OCw0MiA1OCw2MiA3NSw1MiIgZmlsbD0iI0U4QzQ2OCIvPjwvc3ZnPg=="

    // Sets the theme before the page paints, so a dark viewer never sees a light flash. Everything else runs from /static/script.js.
    private const val THEME_BOOT =
        "(function(){var t=localStorage.getItem('theme')||(matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light');" +
            "var r=document.documentElement;r.setAttribute('data-theme',t);r.setAttribute('data-theme-color',localStorage.getItem('theme-color')||'system');" +
            "r.setAttribute('data-pure-black',String(localStorage.getItem('pure-black')==='true'));})();"

    /** The service switcher (YouTube / Bilibili / ...). Each links to that service's home; the current one is `active`. */
    // Switching source stays on the same page (Subscriptions and History are per source too); anywhere else it goes to that source's Home.
    private fun serviceSwitcher(
        activeServiceId: Int,
        activeTab: String,
    ): String {
        val target = NAV.firstOrNull { it.tab == activeTab && it.tab != "settings" } ?: NAV.first()
        val links =
            LocalHttpServer.SUPPORTED_SERVICE_IDS.joinToString("") { id ->
                val active = if (id == activeServiceId) " class=\"active\"" else ""
                "<a href=\"${target.href(id)}\"$active>${WebUi.esc(HtmlRendererCommon.getServiceName(id))}</a>"
            }
        return "<div class=\"service-switcher\">$links</div>"
    }

    private fun railItem(
        item: NavItem,
        serviceId: Int,
        activeTab: String,
    ): String {
        val active = if (item.tab == activeTab) " active" else ""
        return "<a class=\"sidebar-item$active\" href=\"${item.href(serviceId)}\"><span class=\"sidebar-icon\">${WebUi.icon(item.icon)}</span><span class=\"sidebar-label\">${item.label}</span></a>\n"
    }

    private fun barItem(
        item: NavItem,
        serviceId: Int,
        activeTab: String,
    ): String {
        val active = if (item.tab == activeTab) " active" else ""
        return "<a class=\"bottom-nav-item$active\" href=\"${item.href(serviceId)}\"><span class=\"bottom-nav-icon\">${WebUi.icon(item.icon)}</span><span>${item.label}</span></a>\n"
    }

    /** Top bar, rail and bottom bar. [activeTab] is one of youtube (home), subscriptions, history, settings, or anything else for none. */
    fun header(
        activeServiceId: Int,
        query: String?,
        activeTab: String = "youtube",
    ): String {
        val q = WebUi.esc(query.orEmpty())
        val sb = StringBuilder()
        sb.append("<header>\n")
          .append("  <div class=\"top-bar\">\n")
          .append("    <div class=\"top-start\"><a href=\"/?serviceId=$activeServiceId\" class=\"logo\">$LOGO_SVG<span class=\"logo-text\">Fathom</span></a></div>\n")
          .append("    <form action=\"/search\" method=\"GET\" class=\"search-form\">\n")
          .append("      <input type=\"hidden\" name=\"serviceId\" value=\"$activeServiceId\">\n")
          .append("      <input type=\"text\" name=\"q\" class=\"search-input\" placeholder=\"Search\" value=\"$q\" required>\n")
          .append("      <span class=\"search-hint\">${WebUi.icon("smartphone")}Type on your phone</span>\n")
          .append("      <button type=\"submit\" class=\"icon-btn search-btn\" aria-label=\"Search\">${WebUi.icon("search")}</button>\n")
          .append("      <div class=\"search-suggestions\" id=\"search-suggestions-box\"></div>\n")
          .append("    </form>\n")
          .append("    <div class=\"top-end\">\n")
          .append("      ${serviceSwitcher(activeServiceId, activeTab)}\n")
          .append("      <button type=\"button\" id=\"connect-remote-btn\" class=\"icon-btn\" data-action=\"connect-remote\" aria-label=\"Connect remote\">${WebUi.icon("cast")}</button>\n")
          .append("      <button type=\"button\" id=\"theme-toggle\" class=\"icon-btn\" aria-label=\"Toggle theme\"><span class=\"theme-icon-light\">${WebUi.icon("light_mode")}</span><span class=\"theme-icon-dark\">${WebUi.icon("dark_mode")}</span></button>\n")
          .append("    </div>\n")
          .append("  </div>\n")
          .append("</header>\n")
          .append("<nav class=\"sidebar-nav\">\n")
        NAV.forEach { sb.append(railItem(it, activeServiceId, activeTab)) }
        sb.append("</nav>\n<nav class=\"bottom-nav\">\n")
        NAV.filter { it.onPhone }.forEach { sb.append(barItem(it, activeServiceId, activeTab)) }
        return sb.append("</nav>\n").toString()
    }

    /** A complete HTML document. [needsPlayer] adds the Vidstack player's files (watch and audio pages only). */
    fun page(
        title: String?,
        body: String,
        isTv: Boolean,
        needsPlayer: Boolean = false,
    ): String {
        val player =
            if (needsPlayer) {
                // Vidstack is pinned: npm's "latest" tag still points at the old 0.6.x line.
                "  <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/vidstack@1.15.6/player/styles/default/theme.css\">\n" +
                    "  <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/vidstack@1.15.6/player/styles/default/layouts/video.css\">\n" +
                    "  <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/vidstack@1.15.6/player/styles/default/layouts/audio.css\">\n" +
                    "  <script type=\"module\" src=\"https://cdn.jsdelivr.net/npm/vidstack@1.15.6/cdn/with-layouts/vidstack.js\"></script>\n"
            } else {
                ""
            }
        val version = HtmlRendererCommon.STATIC_ASSET_VERSION
        return "<!DOCTYPE html>\n<html>\n<head>\n" +
            "  <meta charset=\"UTF-8\">\n" +
            // Bilibili's image CDN rejects a Referer from this server; a page-wide policy also covers images added later.
            "  <meta name=\"referrer\" content=\"no-referrer\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <title>${WebUi.esc(title)}</title>\n" +
            "  <link rel=\"icon\" type=\"image/svg+xml\" media=\"(prefers-color-scheme: light)\" href=\"$FAVICON_LIGHT\">\n" +
            "  <link rel=\"icon\" type=\"image/svg+xml\" media=\"(prefers-color-scheme: dark)\" href=\"$FAVICON_DARK\">\n" +
            "  <link rel=\"icon\" type=\"image/svg+xml\" href=\"$FAVICON_LIGHT\">\n" +
            // The stylesheet comes first: the player's module script blocks rendering, and would delay finding it.
            "  <link rel=\"stylesheet\" href=\"/static/style.css?v=$version\">\n" +
            player +
            "  <style>\n${WebTheme.deviceColors()}</style>\n" +
            "  <script>$THEME_BOOT</script>\n" +
            "</head>\n" +
            "<body class=\"${if (isTv) "is-tv" else "is-phone"}\">\n" +
            "<div id=\"vptr\" class=\"vptr\" hidden><div class=\"vptr-halo\"></div><div class=\"vptr-disc\"><i></i></div></div>\n" +
            "<div id=\"tv-lock-banner\" class=\"remote-banner\" hidden>" +
            "<span class=\"remote-banner-text\">${WebUi.icon("cast_connected")}Remote connected</span>" +
            "<button type=\"button\" class=\"btn btn-banner\" data-action=\"disconnect-remote\">Disconnect</button></div>\n" +
            body + "\n" +
            "<script src=\"/static/script.js?v=$version\"></script>\n" +
            "</body>\n</html>"
    }

    /** The single-page app's HTML: the template in assets/web/app/index.html with this phone's colours and sources filled in. */
    fun appPage(): String {
        val services =
            LocalHttpServer.SUPPORTED_SERVICE_IDS.joinToString(",") { id ->
                "[$id,${WebUi.jsonString(HtmlRendererCommon.getServiceName(id))}]"
            }
        return WebAssets.appShell
            .replace("{{version}}", WebAssets.appVersion)
            .replace("{{deviceColors}}", WebTheme.deviceColors(":root[data-scheme=\"light\"]", ":root[data-scheme=\"dark\"]"))
            .replace("{{services}}", "[$services]")
    }
}
