package io.github.aedev.flow.localserver

import android.content.Context

/**
 * The web UI's stylesheet and script. They are real files under `assets/web` (so they can be edited, syntax-checked
 * and opened in a browser like any web project), joined here in a fixed order and served as `/static/style.css` and
 * `/static/script.js`.
 */
object WebAssets {
    // Order matters: later files may use functions and rules from earlier ones.
    private val CSS_FILES =
        listOf(
            "css/tokens.css",
            "css/themes.css",
            "css/base.css",
            "css/shell.css",
            "css/cards.css",
            "css/buttons.css",
            "css/watch.css",
            "css/player.css",
            "css/channel.css",
            "css/settings.css",
            "css/dialogs.css",
            "css/remote.css",
        )
    private val JS_FILES =
        listOf(
            "js/theme.js",
            "js/core.js",
            "js/share.js",
            "js/remote.js",
            "js/nav.js",
            "js/settings.js",
            "js/search.js",
            "js/actions.js",
            "js/loader.js",
            "js/player.js",
        )

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
        val css: String,
        val js: String,
        val version: String,
        val appCss: String,
        val appJs: String,
        val appShell: String,
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
            val css = join(app, CSS_FILES)
            val js = join(app, JS_FILES)
            // A hash of the content: a change to any file changes the ?v= of both URLs, so they can be cached forever.
            val appCss = join(app, APP_CSS_FILES)
            val appJs = join(app, APP_JS_FILES)
            val appShell = join(app, listOf("app/index.html"))
            bundle =
                Bundle(
                    css,
                    js,
                    Integer.toHexString(css.hashCode() * 31 + js.hashCode()),
                    appCss,
                    appJs,
                    appShell,
                    Integer.toHexString(appCss.hashCode() * 31 + appJs.hashCode()),
                )
        }
    }

    val css: String get() = requireBundle().css
    val js: String get() = requireBundle().js

    val appCss: String get() = requireBundle().appCss
    val appJs: String get() = requireBundle().appJs
    val appShell: String get() = requireBundle().appShell

    /** Cache-busting tag for /app.css and /app.js. */
    val appVersion: String get() = requireBundle().appVersion

    /** Cache-busting tag for /static/style.css and /static/script.js. */
    val version: String get() = requireBundle().version

    private fun requireBundle(): Bundle = checkNotNull(bundle) { "WebAssets.init(context) must run before the web UI is served" }

    private fun join(
        context: Context,
        files: List<String>,
    ): String =
        files.joinToString("\n") { name ->
            context.assets.open("web/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
}
