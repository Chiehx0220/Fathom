package io.github.aedev.flow.localserver

import android.content.Context

/**
 * Stylesheet and script of the single-page app, joined from `assets/web/app` in a fixed order and served as `/app.css`
 * and `/app.js`. `assets/web/app/strings/<language>.json` are embedded in the page as one object keyed by language.
 */
object WebAssets {
    // The single-page app served at /app: its own stylesheet and script, and the HTML shell they are opened from.
    private val APP_CSS_FILES =
        listOf(
            "app/css/fonts.css",
            "app/css/tokens.css",
            "app/css/shell.css",
            "app/css/cards.css",
            "app/css/screens.css",
            "app/css/watch.css",
            "app/css/overlay.css",
        )
    private val APP_JS_FILES =
        listOf(
            "app/js/util.js",
            "app/js/store.js",
            "app/js/theme.js",
            "app/js/api.js",
            "app/js/ui.js",
            "app/js/focus.js",
            "app/js/menu.js",
            "app/js/router.js",
            "app/js/player.js",
            "app/js/remote.js",
            "app/js/home.js",
            "app/js/list.js",
            "app/js/watch.js",
            "app/js/channel.js",
            "app/js/settings.js",
            "app/js/boot.js",
        )

    private class Bundle(
        val appCss: String,
        val appJs: String,
        val appShell: String,
        val appStrings: String,
        val appVersion: String,
    )

    @Volatile
    private var bundle: Bundle? = null

    /** Reads the files once; called when the server is created. */
    fun init(context: Context) {
        if (bundle != null) return
        synchronized(this) {
            if (bundle != null) return
            val app = context.applicationContext
            val appCss = join(app, APP_CSS_FILES)
            val appJs = join(app, APP_JS_FILES)
            val appShell = join(app, listOf("app/index.html"))
            val appStrings = stringsObject(app)
            // The vendored player is part of the version too: its entry files are asked for with it, so a new player is fetched anew.
            val vendorVersion = join(app, listOf("vendor/videojs/VERSION"))
            bundle =
                Bundle(
                    appCss,
                    appJs,
                    appShell,
                    appStrings,
                    // Content hash: any change to the files, strings or vendored player busts the ?v= cache key.
                    Integer.toHexString(
                        ((appCss.hashCode() * 31 + appJs.hashCode()) * 31 + vendorVersion.hashCode()) * 31 + appStrings.hashCode(),
                    ),
                )
        }
    }

    val appCss: String get() = requireBundle().appCss
    val appJs: String get() = requireBundle().appJs
    val appShell: String get() = requireBundle().appShell

    /** All language files as one JSON object, safe inside a script element. */
    val appStrings: String get() = requireBundle().appStrings

    /** Cache-busting tag for /app.css and /app.js. */
    val appVersion: String get() = requireBundle().appVersion

    private fun stringsObject(context: Context): String =
        (context.assets.list("web/app/strings") ?: emptyArray())
            .filter { it.endsWith(".json") }
            .sorted()
            .joinToString(",", "{", "}") { name ->
                val words =
                    context.assets
                        .open("web/app/strings/$name")
                        .bufferedReader(Charsets.UTF_8)
                        .use { it.readText() }
                "\"${name.removeSuffix(".json")}\":${words.trim().replace("</", "<\\/")}"
            }

    private fun requireBundle(): Bundle = checkNotNull(bundle) { "WebAssets.init(context) must run before the web UI is served" }

    private fun join(
        context: Context,
        files: List<String>,
    ): String =
        files.joinToString("\n") { name ->
            context.assets
                .open("web/$name")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }
}
