package org.schabi.newpipe.localserver

import android.util.Base64

import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale

// Shared rendering toolbox (page-frame template, grid renderer, thumbnail/date/count formatting,
// HTML/JS escaping, theme CSS) used by every other HtmlRenderer* page file.
object HtmlRendererCommon {

    // Fallback avatar palette, keyed deterministically by name hash via avatarColorFor().
    @JvmField
    val AVATAR_COLORS = arrayOf("#ff5722", "#e91e63", "#9c27b0", "#673ab7", "#3f51b5", "#2196f3", "#03a9f4", "#00bcd4", "#009688", "#4caf50", "#8bc34a", "#cddc39", "#ffc107", "#ff9800")

    // Trims a leading "@" first - comment authors are often handles ("@name"), not display names.
    @JvmStatic
    fun avatarInitial(name: String?): String {
        val trimmed = name?.trimStart('@')
        return if (!trimmed.isNullOrEmpty()) trimmed.substring(0, 1).uppercase() else "?"
    }

    @JvmStatic
    fun avatarColorFor(name: String): String = AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.size]

    // Cache-busting tag for /static/style.css and /static/script.js, derived from content - no
    // version number to bump manually. Combines two hashCode() calls rather than concatenating
    // HtmlStyles.CSS + HtmlScripts.SCRIPTS first: both are compile-time-constant Strings, and
    // javac would fold that ~60KB "+" into a single constant-pool entry, exceeding the JVM's
    // 65535-byte-per-entry limit ("constant string too long").
    @JvmField
    val STATIC_ASSET_VERSION: String = Integer.toHexString(HtmlStyles.CSS.hashCode() * 31 + HtmlScripts.SCRIPTS.hashCode())

    /**
     * Service display name. Delegates to the extractor so the name always matches whatever
     * PipePipeExtractor has registered — the previous `String[]` indexed by serviceId threw
     * ArrayIndexOutOfBounds as soon as a non-YouTube service (BiliBili is id 5) was selected.
     */
    @JvmStatic
    fun getServiceName(serviceId: Int): String {
        return try {
            val name = org.schabi.newpipe.extractor.NewPipe.getService(serviceId).serviceInfo.name
            if (name.isNullOrEmpty()) "Fathom" else name
        } catch (e: Exception) {
            "Fathom"
        }
    }

    // Serialize a Page object to a Base64 string for URL injection
    @JvmStatic
    fun serializePage(page: Page?): String? {
        if (page == null) return null
        return try {
            val baos = ByteArrayOutputStream()
            val oos = ObjectOutputStream(baos)
            oos.writeObject(page)
            oos.close()
            Base64.encodeToString(baos.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // serializePage(page) + escapeJs(...) in one step, for a "Load More" button's onclick literal.
    // Null for both "no next page" and "serialization failed" - doubles as the render-button check.
    @JvmStatic
    fun serializePageJs(page: Page?): String? {
        val serialized = serializePage(page) ?: return null
        return escapeJs(serialized)
    }

    // Deserialize a Page object from a Base64 URL parameter
    @JvmStatic
    fun deserializePage(b64: String?): Page? {
        if (b64.isNullOrEmpty()) return null
        return try {
            val bytes = Base64.decode(b64, Base64.URL_SAFE)
            val bais = ByteArrayInputStream(bytes)
            val ois = ObjectInputStream(bais)
            val page = ois.readObject() as Page
            ois.close()
            page
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Emits the CSS variables that the original dynamic-colour block left untouched.
     *
     * The stylesheet declares ~56 variables but only 18 were being overridden with the device's
     * Material You palette. The remainder kept their baseline purple-tinted literals, so a neutral
     * dynamic palette produced a UI that mixed grey surfaces with lavender accents — the main
     * reason it stopped reading as Material 3. Everything here is derived from the same scheme so
     * the whole page comes from one tonal source, and MD3 roles the CSS needs (onPrimary for
     * filled-button labels, state-layer tints) are exposed too.
     */
    private fun derivedThemeVars(c: Map<String, String>, dark: Boolean): String {
        val onSurface = c.getOrDefault("onSurface", if (dark) "#e6e1e5" else "#1d1b20")
        val onSurfaceVariant = c.getOrDefault("onSurfaceVariant", if (dark) "#cac4d0" else "#49454f")
        val surfaceHigh = c.getOrDefault("surfaceContainerHigh", if (dark) "#2b2930" else "#ece6f0")
        val outlineVariant = c.getOrDefault("outlineVariant", if (dark) "#49454f" else "#cac4d0")
        val onSecondaryContainer = c.getOrDefault("onSecondaryContainer", if (dark) "#e8def8" else "#1d192b")
        val onPrimary = c.getOrDefault("onPrimary", if (dark) "#381e72" else "#ffffff")

        val sb = StringBuilder()
        fun v(name: String, value: String) {
            sb.append("  --$name: $value;\n")
        }

        // MD3 roles consumed directly by the component CSS below.
        v("md-primary", c.getOrDefault("primary", if (dark) "#d0bcff" else "#6750A4"))
        v("md-on-primary", onPrimary)
        // Built from onPrimary, not onSurface: overlay for controls sitting on a --md-primary
        // fill (e.g. the TV-lock banner's button), not the neutral background.
        v("md-on-primary-state", hexToRgba(onPrimary, 0.16))
        v("md-on-primary-outline", hexToRgba(onPrimary, 0.3))
        v("md-on-secondary-container", onSecondaryContainer)
        v("md-error", c.getOrDefault("error", if (dark) "#f2b8b5" else "#b3261e"))
        v("md-on-error", c.getOrDefault("onError", if (dark) "#601410" else "#ffffff"))
        v("md-error-container", c.getOrDefault("errorContainer", if (dark) "#8c1d18" else "#f9dedc"))
        v("md-on-error-container", c.getOrDefault("onErrorContainer", if (dark) "#f9dedc" else "#410e0b"))
        v("md-on-surface", onSurface)
        v("md-on-surface-variant", onSurfaceVariant)
        v("md-outline-variant", outlineVariant)
        v("md-surface-high", surfaceHigh)
        // State layers: MD3 uses an onSurface overlay at 8%/12% for hover/pressed.
        v("md-state-hover", hexToRgba(onSurface, 0.08))
        v("md-state-press", hexToRgba(onSurface, 0.12))

        // Previously-stale variables, now tied to the same scheme.
        v("card-thumbnail-bg", surfaceHigh)
        v("card-title-color", onSurface)
        v("card-meta-color", onSurfaceVariant)
        v("media-desc-bg", surfaceHigh)
        v("media-desc-color", onSurface)
        v("media-title-color", onSurface)
        v("media-stats-color", onSurfaceVariant)
        v("uploader-name-color", onSurface)
        v("uploader-subs-color", onSurfaceVariant)
        v("comment-author-color", onSurface)
        v("comment-text-color", onSurface)
        v("comment-time-color", onSurfaceVariant)
        v("comment-count-color", onSurface)
        v("channel-name-color", onSurface)
        v("channel-desc-color", onSurfaceVariant)
        v("settings-title-color", onSurface)
        v("setting-label-color", onSurface)
        v("setting-desc-color", onSurfaceVariant)
        v("textarea-color", onSurface)
        v("textarea-label-color", onSurface)
        v("service-tab-color", onSurfaceVariant)
        v("service-tab-hover-color", onSecondaryContainer)
        v("bottom-nav-item-color", onSurfaceVariant)
        // Hairlines: MD3 uses outlineVariant rather than a flat white/black alpha.
        v("media-info-border", outlineVariant)
        v("comments-border", outlineVariant)
        v("comment-border", outlineVariant)
        v("channel-header-border", outlineVariant)
        v("settings-section-border", outlineVariant)
        return sb.toString()
    }

    /** #RRGGBB -> rgba(r, g, b, alpha), for building MD3 state layers in CSS. */
    private fun hexToRgba(hex: String, alpha: Double): String {
        try {
            var h = hex.trim()
            if (h.startsWith("#")) {
                h = h.substring(1)
            }
            if (h.length == 6) {
                val r = h.substring(0, 2).toInt(16)
                val g = h.substring(2, 4).toInt(16)
                val b = h.substring(4, 6).toInt(16)
                return "rgba($r, $g, $b, $alpha)"
            }
        } catch (ignored: Exception) {
            // Fall through to a neutral overlay.
        }
        return "rgba(127, 127, 127, $alpha)"
    }

    /**
     * A navigation icon from the Material Symbols font.
     *
     * The nav previously used hand-picked SVG paths, which mixed weights badly: some were solid
     * filled glyphs while others were drawn as 1-unit-tall filled rectangles that rendered as
     * hairlines, so the row looked uneven in both stroke width and height. The font guarantees one
     * consistent optical size and weight across every icon, and exposes a FILL axis so the active
     * item can be filled and the rest outlined — which is what Material 3 navigation specifies.
     *
     * @param cls  wrapper class, `sidebar-icon` or `bottom-nav-icon`
     * @param name Material Symbols glyph name
     */
    private fun navIcon(cls: String, name: String): String {
        return "<span class=\"$cls\"><span class=\"material-symbols-rounded\">$name</span></span>"
    }

    /**
     * One entry in the sidebar (desktop/tablet) or bottom nav (phone) - the two render the same
     * item set (minus History on the bottom nav, which has less room) with different CSS classes,
     * so this is shared instead of the item markup being written out twice per item.
     */
    private fun appendNavItem(sb: StringBuilder, itemClass: String, iconClass: String, labeledSpan: Boolean,
                               href: String, activeClass: String, iconName: String, label: String) {
        val icon = navIcon(iconClass, iconName)
        val labelSpanAttr = if (labeledSpan) " class=\"sidebar-label\"" else ""
        sb.append("  <a href=\"$href\" class=\"$itemClass $activeClass\">\n")
          .append("    $icon\n")
          .append("    <span$labelSpanAttr>$label</span>\n")
          .append("  </a>\n")
    }

    /**
     * Renders the YouTube / BiliBili switcher shown in the top bar. Each entry links to the home
     * feed of that service; every page already carries `serviceId` in its URL, so switching
     * is just a navigation and no extra state is needed.
     */
    private fun getServiceSwitcherHtml(activeServiceId: Int): String {
        val sb = StringBuilder()
        sb.append("<div class=\"service-switcher\" style=\"display:flex; align-items:center; gap:4px; margin-left:12px;\">")
        for (serviceId in LocalHttpServer.SUPPORTED_SERVICE_IDS) {
            val active = serviceId == activeServiceId
            val activeStyle = if (active)
                " background:var(--bottom-nav-active-pill-bg, #e8def8); color:var(--bottom-nav-item-active-color, #21005d);"
            else
                " background:transparent; color:var(--bottom-nav-item-color, #49454f);"
            val serviceName = getServiceName(serviceId)
            sb.append("<a href=\"/?serviceId=$serviceId\" style=\"padding:5px 12px; border-radius:16px; font-size:13px; font-weight:600; text-decoration:none; white-space:nowrap;$activeStyle\">$serviceName</a>")
        }
        sb.append("</div>")
        return sb.toString()
    }

    @JvmStatic
    @JvmOverloads
    fun getHeaderHtml(activeServiceId: Int, query: String?, activeTab: String = "youtube"): String {
        val sb = StringBuilder()
        val ytActive = if (activeTab == "youtube") "active" else ""
        val histActive = if (activeTab == "history") "active" else ""
        val subsActive = if (activeTab == "subscriptions") "active" else ""
        val settingsActive = if (activeTab == "settings") "active" else ""

        sb.append("<header>\n")
          .append("  <div class=\"top-bar\">\n")
          .append("    <div style=\"display:flex; align-items:center;\">\n")
          .append("      <a href=\"/?serviceId=$activeServiceId\" class=\"logo\"><svg viewBox=\"0 0 100 100\" width=\"28\" height=\"28\" style=\"display:inline-block; vertical-align:middle; margin-right:6px; border-radius:6px;\"><rect width=\"100\" height=\"100\" rx=\"18\" fill=\"var(--logo-badge-bg)\"/><g stroke=\"var(--logo-badge-fg)\" stroke-width=\"5\" stroke-linecap=\"round\"><line x1=\"37\" y1=\"18\" x2=\"37\" y2=\"78\"/><line x1=\"37\" y1=\"22\" x2=\"76\" y2=\"22\"/><line x1=\"37\" y1=\"42\" x2=\"50\" y2=\"42\"/><line x1=\"37\" y1=\"62\" x2=\"50\" y2=\"62\"/></g><polygon points=\"58,42 58,62 75,52\" fill=\"var(--logo-badge-accent)\"/></svg><span class=\"logo-text\">Fathom</span></a>\n")
          .append("      ${getServiceSwitcherHtml(activeServiceId)}\n")
          .append("    </div>\n")
          .append("    <form action=\"/search\" method=\"GET\" class=\"search-form\">\n")
          .append("      <input type=\"hidden\" name=\"serviceId\" value=\"$activeServiceId\">\n")
          .append("      <input type=\"text\" name=\"q\" class=\"search-input\" placeholder=\"Search\" value=\"${query?.replace("\"", "&quot;") ?: ""}\" required>\n")
          .append("      <button type=\"submit\" class=\"search-btn\"><span class=\"material-symbols-rounded\">search</span></button>\n")
          .append("      <div class=\"search-suggestions\" id=\"search-suggestions-box\"></div>\n")
          .append("    </form>\n")
          .append("    <div style=\"display:flex; align-items:center; gap:10px;\">\n")
          .append("      <button id=\"connect-remote-btn\" class=\"theme-toggle-btn\" onclick=\"playOnTV(window.location.href, document.title)\" aria-label=\"Connect Remote\"><span class=\"material-symbols-rounded\">cast</span></button>\n")
          .append("      <button id=\"theme-toggle\" class=\"theme-toggle-btn\" aria-label=\"Toggle Theme\">\n")
          .append("        <span class=\"theme-icon-light material-symbols-rounded\">light_mode</span>\n")
          .append("        <span class=\"theme-icon-dark material-symbols-rounded\">dark_mode</span>\n")
          .append("      </button>\n")
          .append("    </div>\n")
          .append("  </div>\n")
          .append("</header>\n")

        sb.append("<div class=\"sidebar-nav\">\n")
        appendNavItem(sb, "sidebar-item", "sidebar-icon", true, "/?serviceId=$activeServiceId", ytActive, "home", "Home")
        appendNavItem(sb, "sidebar-item", "sidebar-icon", true, "/subscriptions", subsActive, "subscriptions", "Subscriptions")
        appendNavItem(sb, "sidebar-item", "sidebar-icon", true, "/history", histActive, "history", "History")
        appendNavItem(sb, "sidebar-item", "sidebar-icon", true, "/settings", settingsActive, "settings", "Settings")
        sb.append("</div>\n")

        // Same items as the sidebar above, minus History (less room on a phone-width bottom bar).
        sb.append("<div class=\"bottom-nav\">\n")
        appendNavItem(sb, "bottom-nav-item", "bottom-nav-icon", false, "/?serviceId=$activeServiceId", ytActive, "home", "Home")
        appendNavItem(sb, "bottom-nav-item", "bottom-nav-icon", false, "/subscriptions", subsActive, "subscriptions", "Subscriptions")
        appendNavItem(sb, "bottom-nav-item", "bottom-nav-icon", false, "/settings", settingsActive, "settings", "Settings")
        sb.append("</div>\n")

        return sb.toString()
    }

    // needsPlayer defaults false - most callers don't need the player library.
    @JvmStatic
    @JvmOverloads
    fun wrapInTemplate(title: String?, bodyContent: String, isTv: Boolean, needsPlayer: Boolean = false): String {
        // Escapes raw extractor titles (any YouTube/Bilibili title can contain "&"/"<"/'"') at
        // this single chokepoint, covering every page title without touching each call site.
        val safeTitle = if (title != null) title.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;") else ""
        val bodyClass = if (isTv) "is-tv" else "is-phone"
        // Light/dark favicon variants via prefers-color-scheme media query - favicons are fetched
        // as standalone resources and can't read this page's CSS custom properties like the
        // inline header logo does.
        val faviconLight = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxMDAgMTAwIj48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgcng9IjE4IiBmaWxsPSIjRjJFREUwIi8+PGcgc3Ryb2tlPSIjMEYxRDMzIiBzdHJva2Utd2lkdGg9IjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCI+PGxpbmUgeDE9IjM3IiB5MT0iMTgiIHgyPSIzNyIgeTI9Ijc4Ii8+PGxpbmUgeDE9IjM3IiB5MT0iMjIiIHgyPSI3NiIgeTI9IjIyIi8+PGxpbmUgeDE9IjM3IiB5MT0iNDIiIHgyPSI1MCIgeTI9IjQyIi8+PGxpbmUgeDE9IjM3IiB5MT0iNjIiIHgyPSI1MCIgeTI9IjYyIi8+PC9nPjxwb2x5Z29uIHBvaW50cz0iNTgsNDIgNTgsNjIgNzUsNTIiIGZpbGw9IiNDOTlBMzQiLz48L3N2Zz4K"
        val faviconDark = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxMDAgMTAwIj48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgcng9IjE4IiBmaWxsPSIjMEYxRDMzIi8+PGcgc3Ryb2tlPSIjRjJFREUwIiBzdHJva2Utd2lkdGg9IjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCI+PGxpbmUgeDE9IjM3IiB5MT0iMTgiIHgyPSIzNyIgeTI9Ijc4Ii8+PGxpbmUgeDE9IjM3IiB5MT0iMjIiIHgyPSI3NiIgeTI9IjIyIi8+PGxpbmUgeDE9IjM3IiB5MT0iNDIiIHgyPSI1MCIgeTI9IjQyIi8+PGxpbmUgeDE9IjM3IiB5MT0iNjIiIHgyPSI1MCIgeTI9IjYyIi8+PC9nPjxwb2x5Z29uIHBvaW50cz0iNTgsNDIgNTgsNjIgNzUsNTIiIGZpbGw9IiNFOEM0NjgiLz48L3N2Zz4K"

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                // Bilibili's CDN (i*.hdslb.com) 403s a Referer pointing at this server. Page-wide
                // policy also covers dynamically injected images a per-tag attribute would miss.
                "    <meta name=\"referrer\" content=\"no-referrer\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>$safeTitle</title>\n" +
                "    <link rel=\"icon\" type=\"image/svg+xml\" media=\"(prefers-color-scheme: light)\" href=\"$faviconLight\">\n" +
                "    <link rel=\"icon\" type=\"image/svg+xml\" media=\"(prefers-color-scheme: dark)\" href=\"$faviconDark\">\n" +
                "    <link rel=\"icon\" type=\"image/svg+xml\" href=\"$faviconLight\">\n" +
                // Must precede the cdn.jsdelivr.net <script>/<link> tags below - the module
                // script is render+parser-blocking (no defer honored on type="module" until
                // after fetch), delaying discovery of this <link> until it finishes, leaving
                // the page unstyled longer.
                "    <link rel=\"stylesheet\" href=\"/static/style.css?v=$STATIC_ASSET_VERSION\">\n" +
                (if (needsPlayer)
                    // Version pinned deliberately: npm's "latest" dist-tag for this package still
                    // points at the pre-1.x line (0.6.15) - "next" (1.15.6) is actually the
                    // current stable major. Never use @latest here.
                    "    <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/vidstack@1.15.6/player/styles/default/theme.css\" />\n" +
                        "    <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/npm/vidstack@1.15.6/player/styles/default/layouts/video.css\" />\n" +
                        "    <script type=\"module\" src=\"https://cdn.jsdelivr.net/npm/vidstack@1.15.6/cdn/with-layouts/vidstack.js\"></script>\n"
                else "") +
                "    <style>\n" + getCustomThemeCss() + "\n" +
                // Appended last, so it used to beat the MD3 focus rules with a pre-MD3 neon
                // box-shadow on every focused control - removed for one focus treatment everywhere.
                "        #share-modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.65); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); z-index: 100000; display: none; align-items: center; justify-content: center; opacity: 0; transition: opacity 0.25s ease; }\n" +
                "        #share-modal-overlay.active { display: flex; opacity: 1; }\n" +
                "        .share-modal-card { background: var(--card-bg, #1c1b1f); color: var(--text-color, #fff); border: 1px solid rgba(255,255,255,0.15); border-radius: 20px; padding: 20px; width: 90%; max-width: 400px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); transform: translateY(20px); transition: transform 0.25s ease; font-family: 'Roboto', sans-serif; }\n" +
                "        #share-modal-overlay.active .share-modal-card { transform: translateY(0); }\n" +
                "        .share-modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }\n" +
                "        .share-modal-title { font-size: 18px; font-weight: 600; }\n" +
                "        .share-modal-close { background: none; border: none; color: currentColor; font-size: 24px; cursor: pointer; opacity: 0.7; line-height: 1; }\n" +
                "        .share-link-box { display: flex; gap: 8px; margin-bottom: 20px; background: rgba(255,255,255,0.06); border-radius: 12px; padding: 4px 6px 4px 12px; border: 1px solid rgba(255,255,255,0.1); align-items: center; }\n" +
                "        .share-link-input { flex: 1; background: none; border: none; color: inherit; font-size: 13px; outline: none; text-overflow: ellipsis; white-space: nowrap; overflow: hidden; }\n" +
                "        .share-copy-btn { background: var(--logo-color, #7c3aed); color: #fff; border: none; border-radius: 8px; padding: 8px 16px; font-size: 13px; font-weight: 600; cursor: pointer; transition: background 0.2s; flex-shrink: 0; }\n" +
                "        .share-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; text-align: center; }\n" +
                "        .share-item { display: flex; flex-direction: column; align-items: center; gap: 6px; text-decoration: none; color: inherit; font-size: 12px; opacity: 0.85; transition: opacity 0.2s, transform 0.2s; }\n" +
                "        .share-item:hover { opacity: 1; transform: translateY(-2px); }\n" +
                "        .share-icon-btn { width: 48px; height: 48px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; }\n" +
                "    </style>\n" +
                "    <script>\n" +
                "        (function() {\n" +
                "            const savedTheme = localStorage.getItem('theme');\n" +
                "            const theme = savedTheme ? savedTheme : (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');\n" +
                "            document.documentElement.setAttribute('data-theme', theme);\n" +
                "            \n" +
                "            const accent = localStorage.getItem('theme-color') || 'system';\n" +
                "            document.documentElement.setAttribute('data-theme-color', accent);\n" +
                "            \n" +
                "            const pureBlack = localStorage.getItem('pure-black') === 'true';\n" +
                "            document.documentElement.setAttribute('data-pure-black', pureBlack);\n" +
                "        })();\n" +
                "    </script>\n" +
                "</head>\n" +
                "<body class=\"$bodyClass\">\n" +
                "    <!-- Virtual Mouse Cursor -->\n" +
                "    <div id=\"vptr\" style=\"position:fixed; width:22px; height:22px; border-radius:50%; pointer-events:none; z-index:2147483647; display:none; transform:translate(-50%,-50%); transition:left 0.04s linear, top 0.04s linear;\">\n" +
                "      <svg width=\"22\" height=\"22\" viewBox=\"0 0 22 22\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n" +
                "        <defs><radialGradient id=\"cg\" cx=\"40%\" cy=\"30%\" r=\"70%\"><stop offset=\"0%\" stop-color=\"#e879f9\"/><stop offset=\"100%\" stop-color=\"#7c3aed\"/></radialGradient></defs>\n" +
                "        <circle cx=\"11\" cy=\"11\" r=\"9\" fill=\"url(#cg)\" stroke=\"white\" stroke-width=\"2\"/>\n" +
                "        <circle cx=\"11\" cy=\"11\" r=\"3\" fill=\"white\" fill-opacity=\"0.8\"/>\n" +
                "      </svg>\n" +
                "    </div>\n" +
                "    <div id=\"tv-lock-banner\" style=\"display:none; flex-direction:column; background: var(--md-primary); color: var(--md-on-primary); padding: 0 24px; position: fixed; top: 0; left: 0; right: 0; height: 40px; justify-content: center; z-index: 10001; box-shadow: 0 2px 6px rgba(0,0,0,0.2);\">\n" +
                "      <div style=\"display:flex; align-items:center; justify-content:space-between; width:100%; font-weight:500; font-size:14px;\">\n" +
                "        <span>📺 Currently controlling TV playback</span>\n" +
                "        <div style=\"display:flex; gap:10px;\">\n" +
                "          <button onclick=\"releaseTVLock()\" style=\"background: var(--md-on-primary-state); border: 1px solid var(--md-on-primary-outline); color: var(--md-on-primary); padding: 6px 16px; border-radius: 20px; font-family: inherit; font-size: 12px; font-weight: 600; cursor: pointer; transition: background 0.2s;\">Disconnect / Stop</button>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "    </div>\n" +
                bodyContent + "\n" +
                "    <script src=\"/static/script.js?v=$STATIC_ASSET_VERSION\"></script>\n" +
                "</body>\n" +
                "</html>"
    }

    /**
     * Subscriptions/bookmarked playlists/watch-later can mix items from multiple services (a
     * user can subscribe to YouTube and BiliBili channels alike, and they all sit in one list),
     * but the page-level serviceId only reflects whichever tab is active in the UI. Blindly using
     * it for every item's link sent a BiliBili channel to "/channel?serviceId=0&id=...", and
     * serviceId 0 is YouTube - the extractor correctly rejects a bilibili.com URL as invalid for
     * that service. Resolve each item's real service from its own URL instead, falling back to
     * the page's serviceId only if that lookup fails (e.g. same-service pages like search results,
     * where every item already matches the page serviceId and this is just a no-op confirmation).
     */
    private fun resolveServiceId(fallback: Int, url: String): Int {
        return try {
            org.schabi.newpipe.extractor.NewPipe.getServiceByUrl(url).serviceId
        } catch (e: Exception) {
            fallback
        }
    }

    // fallbackAvatarUrl: for a StreamInfoItem with no per-item avatar (e.g. a channel's upload
    // listing) - caller passes its already-fetched channel avatar instead of a colored initial.
    @JvmStatic
    fun renderGrid(sb: StringBuilder, serviceId: Int, items: List<InfoItem>, showDeleteButton: Boolean = false, fallbackAvatarUrl: String? = null) {
        sb.append("  <div class=\"grid\">\n")
        for (item in items) {
            val itemServiceId = resolveServiceId(serviceId, item.url)
            var typeBadge = ""
            val clickUrl: String = when (item.infoType) {
                InfoItem.InfoType.PLAYLIST -> {
                    typeBadge = "📁 Playlist"
                    "/playlist?serviceId=$itemServiceId&id=${item.url}"
                }
                InfoItem.InfoType.CHANNEL -> {
                    typeBadge = "👤 Channel"
                    "/channel?serviceId=$itemServiceId&id=${item.url}"
                }
                else -> "/watch?serviceId=$itemServiceId&id=${item.url}"
            }

            if (item.infoType == InfoItem.InfoType.CHANNEL) {
                val cName = item.name ?: "Channel"
                val firstChar = avatarInitial(cName)
                val avatarBg = avatarColorFor(cName)
                val rawThumb = getThumbnailUrl(item.thumbnailUrl).takeIf { it.isNotBlank() }

                // Subscriber count when available (search/kiosk results have it, subscriptions
                // list doesn't) - more useful than repeating the channel name.
                var subLabel = "Channel"
                if (item is ChannelInfoItem) {
                    val subs = item.subscriberCount
                    if (subs >= 0) {
                        subLabel = formatCount(subs) + " subscribers"
                    }
                }

                // A channel is a list row, not a card - own MD3 list-item treatment, not a bare
                // avatar+text pair.
                val cNameEscaped = escapeHtml(cName)

                sb.append("    <div class=\"card channel-row-card\">\n")
                  .append("      <a href=\"$clickUrl\" style=\"flex-shrink:0; position:relative; display:inline-block;\">\n")

                if (!rawThumb.isNullOrBlank()) {
                    val rawThumbUrl = getThumbnailUrl(rawThumb)
                    sb.append("        <img class=\"channel-card-avatar\" src=\"$rawThumbUrl\" onerror=\"this.style.display='none'; this.nextElementSibling.style.display='flex';\">\n")
                      .append("        <div class=\"channel-card-avatar\" style=\"display:none; background-color:$avatarBg; color:#ffffff; font-weight:700; align-items:center; justify-content:center;\">$firstChar</div>\n")
                } else {
                    sb.append("        <div class=\"channel-card-avatar\" style=\"display:flex; background-color:$avatarBg; color:#ffffff; font-weight:700; align-items:center; justify-content:center;\">$firstChar</div>\n")
                }

                sb.append("      </a>\n")
                  .append("      <div class=\"card-info\" style=\"min-width:0; flex-grow:1; flex-shrink:1; overflow:hidden;\">\n")
                  .append("        <a href=\"$clickUrl\" class=\"card-title\" style=\"display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;\">$cNameEscaped</a>\n")
                  .append("        <div class=\"card-meta\" style=\"margin-top:4px;\">\n")
                  .append("          <span class=\"channel-row-badge\"><span class=\"material-symbols-rounded\">person</span>$subLabel</span>\n")
                  .append("        </div>\n")
                  .append("      </div>\n")
                  .append("    </div>\n")
                continue
            }

            var uploaderName: String?
            var uploaderAvatarUrl: String? = null
            if (item is StreamInfoItem) {
                uploaderName = item.uploaderName
                uploaderAvatarUrl = if (hasThumbnail(item.uploaderAvatarUrl)) getThumbnailUrl(item.uploaderAvatarUrl) else fallbackAvatarUrl
            } else {
                uploaderName = item.name
            }
            if (uploaderName == null) uploaderName = ""
            val firstChar = avatarInitial(uploaderName)
            val avatarBg = avatarColorFor(uploaderName)

            val itemThumb = getThumbnailUrl(item.thumbnailUrl)

            sb.append("    <div class=\"card\">\n")
            if (showDeleteButton) {
                // escapeJs, not encodeUrl: removeHistoryItem() does the one encodeURIComponent()
                // itself - double-encoding here breaks the backend's percent-decode match against
                // the DB's raw URL, silently deleting zero rows.
                val deleteUrlJs = escapeJs(item.url)
                // Plain HTML attribute, not a JS string literal - escapeJs's backslash escapes
                // don't protect against a literal '"' terminating the attribute early. escapeHtml
                // is correct here; deleteSelectedHistory() reads it via .dataset.url (browser
                // HTML-decodes) then applies its own encodeURIComponent().
                val deleteUrlHtml = escapeHtml(item.url)
                sb.append("      <a href=\"$clickUrl\" style=\"position:relative; display:block;\">\n")
                  .append("        <img class=\"card-thumbnail\" src=\"$itemThumb\">\n")
                  .append("        <button type=\"button\" class=\"card-delete-btn\" onclick=\"removeHistoryItem(event, this, '$deleteUrlJs', $itemServiceId)\" aria-label=\"Remove from history\"><span class=\"material-symbols-rounded\">delete</span></button>\n")
                  // stopPropagation only, no preventDefault: preventDefault on a checkbox's click
                  // cancels its own checked-state toggle, making it visually present but un-checkable.
                  .append("        <span class=\"card-select-indicator\">\n")
                  .append("          <input type=\"checkbox\" class=\"card-select-checkbox\" data-url=\"$deleteUrlHtml\" onclick=\"event.stopPropagation();\" onchange=\"updateHistorySelectCount()\" aria-label=\"Select for batch delete\">\n")
                  .append("          <span class=\"material-symbols-rounded card-select-check-icon\">check</span>\n")
                  .append("        </span>\n")
                  .append("      </a>\n")
            } else {
                sb.append("      <a href=\"$clickUrl\">\n")
                  .append("        <img class=\"card-thumbnail\" src=\"$itemThumb\">\n")
                  .append("      </a>\n")
            }
            sb.append("      <div class=\"card-details\">\n")

            // Checked against the raw field, not getThumbnailUrl()'s return value - that helper
            // always returns a non-null stock-photo fallback, so testing its result would always
            // look "present" and never actually fall back to the colored initial.
            if (!uploaderAvatarUrl.isNullOrBlank()) {
                val uploaderAvatarThumb = getThumbnailUrl(uploaderAvatarUrl)
                sb.append("        <img class=\"card-avatar\" src=\"$uploaderAvatarThumb\" onerror=\"this.style.display='none'; this.nextElementSibling.style.display='flex';\">\n")
                  .append("        <div class=\"card-avatar\" style=\"display:none; background-color:$avatarBg;\">$firstChar</div>\n")
            } else {
                sb.append("        <div class=\"card-avatar\" style=\"display:flex; background-color:$avatarBg;\">$firstChar</div>\n")
            }

            val itemNameEscaped = escapeHtml(item.name)

            sb.append("        <div class=\"card-info\">\n")
              .append("          <a href=\"$clickUrl\" class=\"card-title\">$itemNameEscaped</a>\n")
              .append("          <div class=\"card-meta\">\n")

            if (typeBadge.isNotEmpty()) {
                sb.append("            <span style=\"color:#ff0000; font-weight:bold; font-size:11px;\">$typeBadge</span>\n")
            } else if (item is StreamInfoItem) {
                val uploaderNameEscaped = escapeHtml(item.uploaderName)
                val viewsText = if (item.viewCount >= 0) "${formatCount(item.viewCount)} views" else "Live / Dynamic"
                val uploadDateText = formatUploadDate(item.uploadDate, item.textualUploadDate ?: "")
                sb.append("            <a href=\"/channel?serviceId=$itemServiceId&id=${item.uploaderUrl}\" class=\"card-uploader\">$uploaderNameEscaped</a>\n")
                  .append("            <span>👁️ $viewsText • $uploadDateText</span>\n")
            } else {
                sb.append("            <span class=\"card-uploader\">$itemNameEscaped</span>\n")
            }

            sb.append("          </div>\n")
              .append("        </div>\n")
              .append("      </div>\n")
              .append("    </div>\n")
        }
        sb.append("  </div>\n")
    }

    // IMPORTANT: both getThumbnailUrl() overloads always return non-null/non-empty - never call
    // them to test "has an image?" (indistinguishable from a real URL); check the raw field
    // (List<Image>/String) for null/empty first. Got this backwards twice already, both times
    // showing this placeholder for every card instead of the per-channel avatar.
    private const val NO_THUMBNAIL_PLACEHOLDER =
        "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?q=80&w=300&auto=format&fit=crop"

    private fun normalizeImageUrl(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    }

    @JvmStatic
    fun getThumbnailUrl(thumbnails: List<Image>?): String {
        if (thumbnails != null && thumbnails.isNotEmpty()) {
            for (i in thumbnails.indices.reversed()) {
                val img = thumbnails[i]
                if (img != null && !img.url.isNullOrBlank()) {
                    return normalizeImageUrl(img.url)
                }
            }
        }
        return NO_THUMBNAIL_PLACEHOLDER
    }

    /** Raw presence check, unlike [getThumbnailUrl]'s placeholder-substituted output. */
    @JvmStatic
    fun hasThumbnail(thumbnails: List<Image>?): Boolean =
        thumbnails != null && thumbnails.any { it != null && !it.url.isNullOrBlank() }

    // PipePipeExtractor image shape is inconsistent: InfoItem/CommentsInfoItem carry a single URL
    // string, StreamInfo/ChannelExtractor return List<Image>. This overload covers the URL shape.
    @JvmStatic
    fun getThumbnailUrl(url: String?): String {
        if (!url.isNullOrBlank()) {
            return normalizeImageUrl(url)
        }
        return NO_THUMBNAIL_PLACEHOLDER
    }

    /** Raw presence check for the singular-URL shape, matching [hasThumbnail]'s list overload. */
    @JvmStatic
    fun hasThumbnail(url: String?): Boolean = !url.isNullOrBlank()

    // YoutubeService forces hl=zu (Zulu) to stop YouTube auto-translating titles - side effect:
    // getTextualUploadDate() returns a Zulu relative-time phrase. DateWrapper is still reliable
    // (parsed from that same string), so format it ourselves instead of showing raw Zulu text.
    @JvmStatic
    fun formatUploadDate(uploadDate: DateWrapper?, textualFallback: String?): String {
        if (uploadDate != null) {
            return uploadDate.offsetDateTime().toLocalDate().toString()
        }
        return textualFallback ?: ""
    }

    // Subscribe/Subscribed pill, shared across channel page and both watch pages instead of
    // duplicating toggleSubscribe() wiring three times. backUrlEncoded: each caller's own
    // /subscribe redirect target.
    @JvmStatic
    fun renderSubscribeButton(uploaderUrl: String, uploaderName: String, uploaderAvatarUrl: String, backUrlEncoded: String, isSubscribed: Boolean): String {
        val uploaderUrlEncoded = encodeUrl(uploaderUrl)
        val uploaderUrlJs = escapeJs(uploaderUrl)
        val uploaderNameJs = escapeJs(uploaderName)
        val uploaderAvatarJs = escapeJs(uploaderAvatarUrl)
        return if (isSubscribed) {
            "<a href=\"/subscribe?action=unsubscribe&id=$uploaderUrlEncoded&back=$backUrlEncoded\" onclick=\"toggleSubscribe(event, this, '$uploaderUrlJs', '$uploaderNameJs', '$uploaderAvatarJs')\" class=\"subscribe-btn subscribed\">Subscribed</a>\n"
        } else {
            val uploaderNameEncoded = encodeUrl(uploaderName)
            val uploaderAvatarEncoded = encodeUrl(uploaderAvatarUrl)
            "<a href=\"/subscribe?action=subscribe&id=$uploaderUrlEncoded&name=$uploaderNameEncoded&avatar=$uploaderAvatarEncoded&back=$backUrlEncoded\" onclick=\"toggleSubscribe(event, this, '$uploaderUrlJs', '$uploaderNameJs', '$uploaderAvatarJs')\" class=\"subscribe-btn\">Subscribe</a>\n"
        }
    }

    // Shared so watch and audio pages render an identical Watch Later control.
    @JvmStatic
    fun renderWatchLaterButton(info: StreamInfo, serviceId: Int, isWatchLater: Boolean): String {
        val action = if (isWatchLater) "remove" else "add"
        val cls = if (isWatchLater) "action-pill-btn watch-later-btn added" else "action-pill-btn watch-later-btn"
        val label = if (isWatchLater) "Saved" else "Watch Later"
        val icon =
            if (isWatchLater) {
                "<span class=\"material-symbols-rounded\" style=\"font-size:18px;\">check</span>"
            } else {
                "<span class=\"material-symbols-rounded\" style=\"font-size:18px;\">schedule</span>"
            }
        val thumb = getThumbnailUrl(info.thumbnails)

        val urlEncoded = encodeUrl(info.url)
        val titleEncoded = encodeUrl(info.name)
        val uploaderEncoded = encodeUrl(info.uploaderName)
        val thumbEncoded = encodeUrl(thumb)
        val uploaderUrlEncoded = encodeUrl(info.uploaderUrl)

        val urlJs = escapeJs(info.url)
        val titleJs = escapeJs(info.name)
        val uploaderJs = escapeJs(info.uploaderName)
        val thumbJs = escapeJs(thumb)
        val uploaderUrlJs = escapeJs(info.uploaderUrl)

        return "<a href=\"/watch_later_action?action=$action&url=$urlEncoded&title=$titleEncoded&uploader=$uploaderEncoded&thumbnail=$thumbEncoded&type=video&serviceId=$serviceId&uploaderUrl=$uploaderUrlEncoded\" " +
            "onclick=\"toggleWatchLater(event, this, '$urlJs', '$titleJs', '$uploaderJs', '$thumbJs', $serviceId, '$uploaderUrlJs')\" " +
            "class=\"$cls\">$icon$label</a>\n"
    }

    // Shared so video/audio watch pages render identical like/dislike buttons. includeDislike is
    // false on the audio page (never had one).
    @JvmStatic
    @JvmOverloads
    fun renderLikeDislikePill(info: StreamInfo, likeState: String?, includeDislike: Boolean = true): String {
        val likesText = if (info.likeCount >= 0) formatCount(info.likeCount) else "Like"
        val thumb = getThumbnailUrl(info.thumbnails)

        val urlJs = escapeJs(info.url)
        val titleJs = escapeJs(info.name)
        val uploaderJs = escapeJs(info.uploaderName)
        val thumbJs = escapeJs(thumb)
        val uploaderUrlJs = escapeJs(info.uploaderUrl)

        val likeActive = if (likeState == "LIKED") " active" else ""
        val dislikeActive = if (likeState == "DISLIKED") " active" else ""

        val sb = StringBuilder()
        sb.append("<div class=\"like-dislike-pill\">\n")
          .append("  <button type=\"button\" class=\"pill-btn like-btn$likeActive\" onclick=\"toggleLikeState(event, this, 'like', '$urlJs', '$titleJs', '$uploaderJs', '$thumbJs', '$uploaderUrlJs')\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"16\" height=\"16\"><path d=\"M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z\"/></svg> $likesText</button>\n")
        if (includeDislike) {
            sb.append("  <div class=\"pill-divider\"></div>\n")
              .append("  <button type=\"button\" class=\"pill-btn dislike-btn$dislikeActive\" onclick=\"toggleLikeState(event, this, 'dislike', '$urlJs', '$titleJs', '$uploaderJs', '$thumbJs', '$uploaderUrlJs')\"><svg viewBox=\"0 0 24 24\" fill=\"currentColor\" width=\"16\" height=\"16\" style=\"transform:scaleY(-1);\"><path d=\"M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z\"/></svg></button>\n")
        }
        sb.append("</div>\n")
        return sb.toString()
    }

    @JvmStatic
    fun formatCount(count: Long): String {
        if (count < 0) return ""
        return if (count < 1000) {
            count.toString()
        } else if (count < 1000000) {
            val v = count / 1000.0
            if (v >= 100) {
                String.format(Locale.US, "%.0fK", v)
            } else {
                String.format(Locale.US, "%.1fK", v).replace(".0K", "K")
            }
        } else if (count < 1000000000) {
            val v = count / 1000000.0
            if (v >= 100) {
                String.format(Locale.US, "%.0fM", v)
            } else {
                String.format(Locale.US, "%.1fM", v).replace(".0M", "M")
            }
        } else {
            val v = count / 1000000000.0
            String.format(Locale.US, "%.1fB", v).replace(".0B", "B")
        }
    }

    @JvmStatic
    fun encodeUrl(url: String?): String {
        return try {
            java.net.URLEncoder.encode(url, "UTF-8")
        } catch (e: Exception) {
            url ?: ""
        }
    }

    // Most call sites wrap this in a single-quoted JS literal (onclick="toggleX(...,'...')") -
    // must escape the apostrophe too, not just backslash/double-quote, or a title like "Don't Stop"
    // breaks the onclick syntax.
    @JvmStatic
    fun escapeJs(str: String?): String {
        if (str == null) return ""
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    // For raw extractor titles/uploader names appended as HTML text content, outside
    // wrapInTemplate()'s <title> chokepoint - "&"/"<"/">" would otherwise break/corrupt markup.
    // Also escapes '"' so it's safe in a double-quoted attribute too, a no-op for text-content
    // sites. Deliberately does NOT touch getDescription() content - already rich HTML, would
    // double-encode.
    @JvmStatic
    fun escapeHtml(str: String?): String {
        if (str == null) return ""
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    private fun getCustomThemeCss(): String {
        val sb = StringBuilder()
        if (HtmlRenderer.lightColors.isNotEmpty()) {
            val lc = HtmlRenderer.lightColors
            fun v(name: String, key: String, default: String) {
                sb.append("  --$name: ${lc.getOrDefault(key, default)};\n")
            }
            sb.append(":root {\n")
            v("bg-color", "surface", "#fbfafe")
            v("text-color", "onSurface", "#1d1b20")
            v("header-bg", "surfaceContainer", "#f3f4f9")
            v("logo-color", "primary", "#6750A4")
            v("search-input-bg", "surfaceContainerHigh", "#ece6f0")
            v("search-btn-bg", "surfaceContainerHigh", "#ece6f0")
            v("search-btn-hover", "secondaryContainer", "#e8def8")
            v("service-tab-bg", "surfaceContainerHigh", "#ece6f0")
            v("service-tab-hover-bg", "secondaryContainer", "#e8def8")
            v("card-bg", "surfaceContainerLow", "#ffffff")
            v("bottom-nav-bg", "surfaceContainer", "#f3f4f9")
            v("bottom-nav-active-pill-bg", "secondaryContainer", "#e8def8")
            v("bottom-nav-item-active-color", "primary", "#21005d")
            v("settings-card-bg", "surfaceContainerLow", "#ffffff")
            v("settings-section-title-color", "primary", "#6750A4")
            v("textarea-border", "outline", "#79747e")
            v("textarea-bg", "surfaceContainerLow", "#ffffff")
            v("slider-bg", "secondaryContainer", "#e8def8")
            sb.append(derivedThemeVars(lc, false))
              .append("}\n")
        }
        if (HtmlRenderer.darkColors.isNotEmpty()) {
            val dc = HtmlRenderer.darkColors
            fun v(name: String, key: String, default: String) {
                sb.append("  --$name: ${dc.getOrDefault(key, default)};\n")
            }
            sb.append("[data-theme=\"dark\"] {\n")
            v("bg-color", "surface", "#141218")
            v("text-color", "onSurface", "#e6e1e5")
            v("header-bg", "surfaceContainer", "#1d1b20")
            v("logo-color", "primary", "#d0bcff")
            v("search-input-bg", "surfaceContainerHigh", "#2b2930")
            v("search-btn-bg", "surfaceContainerHigh", "#2b2930")
            v("search-btn-hover", "secondaryContainer", "#4a4458")
            v("service-tab-bg", "surfaceContainerHigh", "#2b2930")
            v("service-tab-hover-bg", "secondaryContainer", "#4a4458")
            v("card-bg", "surfaceContainerLow", "#1d1b20")
            v("bottom-nav-bg", "surfaceContainer", "#1d1b20")
            v("bottom-nav-active-pill-bg", "secondaryContainer", "#4a4458")
            v("bottom-nav-item-active-color", "primary", "#e8def8")
            v("settings-card-bg", "surfaceContainerLow", "#1d1b20")
            v("settings-section-title-color", "primary", "#d0bcff")
            v("textarea-border", "outline", "#938f99")
            v("textarea-bg", "surfaceContainerLow", "#1d1b20")
            v("slider-bg", "secondaryContainer", "#4a4458")
            sb.append(derivedThemeVars(dc, true))
              .append("}\n")
        }

        sb.append("\n/* Custom color theme overrides */\n")
          .append("[data-theme-color=\"purple\"] {\n")
          .append("  --logo-color: #6750A4;\n")
          .append("  --bottom-nav-item-active-color: #21005d;\n")
          .append("  --bottom-nav-active-pill-bg: #e8def8;\n")
          .append("  --settings-section-title-color: #6750A4;\n")
          .append("  --slider-bg: #e8def8;\n")
          .append("  --search-btn-hover: #e8def8;\n")
          .append("  --service-tab-hover-bg: #e8def8;\n")
          .append("}\n")
          .append("[data-theme=\"dark\"][data-theme-color=\"purple\"] {\n")
          .append("  --bg-color: #000000;\n")
          .append("  --header-bg: #121212;\n")
          .append("  --card-bg: #121212;\n")
          .append("  --bottom-nav-bg: #121212;\n")
          .append("  --settings-card-bg: #121212;\n")
          .append("  --textarea-bg: #121212;\n")
          .append("  --logo-color: #d0bcff;\n")
          .append("  --bottom-nav-item-active-color: #e8def8;\n")
          .append("  --bottom-nav-active-pill-bg: #4a4458;\n")
          .append("  --settings-section-title-color: #d0bcff;\n")
          .append("  --slider-bg: #4a4458;\n")
          .append("  --search-btn-hover: #4a4458;\n")
          .append("  --service-tab-hover-bg: #4a4458;\n")
          .append("}\n")
          .append("[data-theme-color=\"green\"] {\n")
          .append("  --logo-color: #2E7D32;\n")
          .append("  --bottom-nav-item-active-color: #1B5E20;\n")
          .append("  --bottom-nav-active-pill-bg: #C8E6C9;\n")
          .append("  --settings-section-title-color: #2E7D32;\n")
          .append("  --slider-bg: #C8E6C9;\n")
          .append("  --search-btn-hover: #C8E6C9;\n")
          .append("  --service-tab-hover-bg: #C8E6C9;\n")
          .append("}\n")
          .append("[data-theme=\"dark\"][data-theme-color=\"green\"] {\n")
          .append("  --bg-color: #000000;\n")
          .append("  --header-bg: #121212;\n")
          .append("  --card-bg: #121212;\n")
          .append("  --bottom-nav-bg: #121212;\n")
          .append("  --settings-card-bg: #121212;\n")
          .append("  --textarea-bg: #121212;\n")
          .append("  --logo-color: #81C784;\n")
          .append("  --bottom-nav-item-active-color: #E8F5E9;\n")
          .append("  --bottom-nav-active-pill-bg: #1B5E20;\n")
          .append("  --settings-section-title-color: #81C784;\n")
          .append("  --slider-bg: #1B5E20;\n")
          .append("  --search-btn-hover: #1B5E20;\n")
          .append("  --service-tab-hover-bg: #1B5E20;\n")
          .append("}\n")
          .append("[data-theme-color=\"blue\"] {\n")
          .append("  --logo-color: #1565C0;\n")
          .append("  --bottom-nav-item-active-color: #0D47A1;\n")
          .append("  --bottom-nav-active-pill-bg: #BBDEFB;\n")
          .append("  --settings-section-title-color: #1565C0;\n")
          .append("  --slider-bg: #BBDEFB;\n")
          .append("  --search-btn-hover: #BBDEFB;\n")
          .append("  --service-tab-hover-bg: #BBDEFB;\n")
          .append("}\n")
          .append("[data-theme=\"dark\"][data-theme-color=\"blue\"] {\n")
          .append("  --bg-color: #000000;\n")
          .append("  --header-bg: #121212;\n")
          .append("  --card-bg: #121212;\n")
          .append("  --bottom-nav-bg: #121212;\n")
          .append("  --settings-card-bg: #121212;\n")
          .append("  --textarea-bg: #121212;\n")
          .append("  --logo-color: #90CAF9;\n")
          .append("  --bottom-nav-item-active-color: #E3F2FD;\n")
          .append("  --bottom-nav-active-pill-bg: #1565C0;\n")
          .append("  --settings-section-title-color: #90CAF9;\n")
          .append("  --slider-bg: #1565C0;\n")
          .append("  --search-btn-hover: #1565C0;\n")
          .append("  --service-tab-hover-bg: #1565C0;\n")
          .append("}\n")
          .append("[data-theme-color=\"orange\"] {\n")
          .append("  --logo-color: #E65100;\n")
          .append("  --bottom-nav-item-active-color: #BF360C;\n")
          .append("  --bottom-nav-active-pill-bg: #FFE0B2;\n")
          .append("  --settings-section-title-color: #E65100;\n")
          .append("  --slider-bg: #FFE0B2;\n")
          .append("  --search-btn-hover: #FFE0B2;\n")
          .append("  --service-tab-hover-bg: #FFE0B2;\n")
          .append("}\n")
          .append("[data-theme=\"dark\"][data-theme-color=\"orange\"] {\n")
          .append("  --bg-color: #000000;\n")
          .append("  --header-bg: #121212;\n")
          .append("  --card-bg: #121212;\n")
          .append("  --bottom-nav-bg: #121212;\n")
          .append("  --settings-card-bg: #121212;\n")
          .append("  --textarea-bg: #121212;\n")
          .append("  --logo-color: #FFB74D;\n")
          .append("  --bottom-nav-item-active-color: #FFF3E0;\n")
          .append("  --bottom-nav-active-pill-bg: #E65100;\n")
          .append("  --settings-section-title-color: #FFB74D;\n")
          .append("  --slider-bg: #E65100;\n")
          .append("  --search-btn-hover: #E65100;\n")
          .append("  --service-tab-hover-bg: #E65100;\n")
          .append("}\n")
          .append("[data-theme-color=\"red\"] {\n")
          .append("  --logo-color: #C62828;\n")
          .append("  --bottom-nav-item-active-color: #B71C1C;\n")
          .append("  --bottom-nav-active-pill-bg: #FFCDD2;\n")
          .append("  --settings-section-title-color: #C62828;\n")
          .append("  --slider-bg: #FFCDD2;\n")
          .append("  --search-btn-hover: #FFCDD2;\n")
          .append("  --service-tab-hover-bg: #FFCDD2;\n")
          .append("}\n")
          .append("[data-theme=\"dark\"][data-theme-color=\"red\"] {\n")
          .append("  --bg-color: #000000;\n")
          .append("  --header-bg: #121212;\n")
          .append("  --card-bg: #121212;\n")
          .append("  --bottom-nav-bg: #121212;\n")
          .append("  --settings-card-bg: #121212;\n")
          .append("  --textarea-bg: #121212;\n")
          .append("  --logo-color: #E57373;\n")
          .append("  --bottom-nav-item-active-color: #FFEBEE;\n")
          .append("  --bottom-nav-active-pill-bg: #C62828;\n")
          .append("  --settings-section-title-color: #E57373;\n")
          .append("  --slider-bg: #C62828;\n")
          .append("  --search-btn-hover: #C62828;\n")
          .append("  --service-tab-hover-bg: #C62828;\n")
          .append("}\n")

        sb.append("\n/* AMOLED Pure Black Override */\n")
          .append("[data-theme=\"dark\"][data-pure-black=\"true\"] {\n")
          .append("  --bg-color: #000000 !important;\n")
          .append("  --header-bg: #121212 !important;\n")
          .append("  --card-bg: #121212 !important;\n")
          .append("  --bottom-nav-bg: #121212 !important;\n")
          .append("  --settings-card-bg: #121212 !important;\n")
          .append("  --textarea-bg: #121212 !important;\n")
          .append("}\n")

        return sb.toString()
    }
}
