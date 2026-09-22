package io.github.aedev.flow.localserver

/** The settings page. The first three preferences are saved on the phone; the rest are kept in this browser (see js/settings.js). */
object HtmlRendererSettings {
    private fun row(
        id: String?,
        label: String,
        description: String,
        control: String,
    ): String {
        val idAttr = if (id == null) "" else " id=\"$id\""
        return "<div class=\"setting-row\"$idAttr>\n" +
            "  <div class=\"setting-text\"><span class=\"setting-label\">${WebUi.esc(label)}</span><span class=\"setting-desc\">${WebUi.esc(description)}</span></div>\n" +
            "  $control\n</div>\n"
    }

    private fun switch(
        id: String,
        name: String? = null,
        checked: Boolean = false,
    ): String {
        val nameAttr = if (name == null) "" else " name=\"$name\" value=\"on\""
        return "<label class=\"switch\"><input type=\"checkbox\" id=\"$id\"$nameAttr${if (checked) " checked" else ""}><span class=\"switch-track\"></span></label>"
    }

    private fun select(
        id: String,
        name: String?,
        options: List<Pair<String, String>>,
        selected: String?,
    ): String {
        val nameAttr = if (name == null) "" else " name=\"$name\""
        val items = options.joinToString("") { (value, label) -> "<option value=\"$value\"${if (value == selected) " selected" else ""}>${WebUi.esc(label)}</option>" }
        return "<select class=\"select\" id=\"$id\"$nameAttr>$items</select>"
    }

    @JvmStatic
    fun renderSettings(
        serviceId: Int,
        hideWatched: Boolean,
        hideShorts: Boolean,
        homeFeedMode: String,
        saved: Boolean,
        isTv: Boolean,
    ): String {
        val feedModes =
            listOf(
                "mix" to "Mix (Recommendations & Subscriptions)",
                "subs" to "Subscriptions Only",
                "recs" to "Recommendations Only",
            )
        val accents =
            listOf(
                "system" to "System (Material You)",
                "purple" to "Classic Purple",
                "green" to "Forest Green",
                "blue" to "Ocean Blue",
                "orange" to "Sunset Orange",
                "red" to "Crimson Red",
            )

        val sb = StringBuilder(WebShell.header(serviceId, "", "settings"))
        sb.append("<main class=\"container\">\n<section class=\"settings-card\">\n")
          .append(WebUi.pageTitle("settings", "Preferences"))
          .append("<div class=\"settings-section\">\n<h2 class=\"settings-section-title\">Filter Settings</h2>\n")
          .append(row(null, "Home Feed Content", "Choose what content appears on your Home feed.", select("setting-home-feed-mode", "home_feed_mode", feedModes, homeFeedMode)))
          .append(row(null, "Hide Watched Videos", "Hide videos you have already watched from lists.", switch("setting-hide-watched", "hide_watched", hideWatched)))
          .append(row(null, "Hide Reels", "Hide vertical videos shorter than 2 minutes.", switch("setting-hide-shorts", "hide_shorts", hideShorts)))
          .append("</div>\n<div class=\"settings-section\">\n<h2 class=\"settings-section-title\">This browser</h2>\n")
          .append(row("mobile-theme-row", "Dark Theme", "Toggle between dark and light appearance.", switch("settings-theme-toggle")))
          .append(row("settings-accent-row", "Theme Accent Color", "Select the primary accent color of the interface.", select("settings-accent-select", null, accents, null)))
          .append(row("settings-pureblack-row", "AMOLED Black", "Use pure black background in dark theme.", switch("settings-pureblack-toggle")))
          .append(row("settings-audio-only-row", "Default to Audio Only", "Always play the audio-only version of videos (Reels are excluded).", switch("settings-audio-only-toggle")))
          .append(row("mobile-history-row", "Watch History", "View your local watch history.", WebUi.button("View", variant = "filled", href = "/history")))
          .append(row(null, "New interface", "Shelves, a persistent player, phone-remote navigation and selectable themes.", WebUi.button("Open", "open_in_new", variant = "filled", href = "/app")))
          .append("</div>\n</section>\n</main>\n")
        return WebShell.page("Settings - Fathom", sb.toString(), isTv)
    }
}
