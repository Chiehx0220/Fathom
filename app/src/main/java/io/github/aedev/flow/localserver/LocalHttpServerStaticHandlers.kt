package io.github.aedev.flow.localserver

import java.io.FileNotFoundException
import java.io.OutputStream

// App shell, CSS, JS; third-party files from assets/web/vendor (e.g. Video.js).

private val VENDOR_PATH = Regex("""/vendor/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*""")

/** Asset path of a /vendor/ request; null if it could escape the vendor folder or is not a plain file name. */
internal fun vendorAssetPath(path: String): String? {
    if (!VENDOR_PATH.matches(path) || path.split('/').any { it == ".." || it == "." }) return null
    return "web" + path
}

internal fun vendorContentType(path: String): String? =
    when (path.substringAfterLast('.', "").lowercase()) {
        "js", "mjs" -> "text/javascript; charset=UTF-8"
        "css" -> "text/css; charset=UTF-8"
        "json" -> "application/json; charset=UTF-8"
        "txt", "md" -> "text/plain; charset=UTF-8"
        else -> null
    }

/**
 * One vendor text file. Video.js chunk names carry a content hash; entry files requested with page version: cacheable forever.
 */
@Throws(Exception::class)
internal fun RequestContext.handleVendorAsset(
    reply: Reply,
    path: String,
) {
    val asset = vendorAssetPath(path)
    val type = vendorContentType(path)
    if (asset == null || type == null) {
        reply.send(404, "Page Not Found", "text/plain; charset=UTF-8")
        return
    }
    val text =
        try {
            dbHelper.appContext.assets
                .open(asset)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (e: FileNotFoundException) {
            reply.send(404, "Page Not Found", "text/plain; charset=UTF-8")
            return
        }
    reply.send(200, text, type, "public, max-age=31536000, immutable")
}

// SPA shell, CSS, JS. ?v= ignored: content change changes WebAssets.appVersion, so max-age=31536000 immutable is safe.
@Throws(Exception::class)
internal fun RequestContext.handleAppShell(reply: Reply) {
    reply.send(200, WebShell.appPage(), "text/html; charset=UTF-8")
}

@Throws(Exception::class)
internal fun RequestContext.handleAppCss(reply: Reply) {
    reply.send(200, WebAssets.appCss, "text/css; charset=UTF-8", "public, max-age=31536000, immutable")
}

@Throws(Exception::class)
internal fun RequestContext.handleAppJs(reply: Reply) {
    reply.send(200, WebAssets.appJs, "application/javascript; charset=UTF-8", "public, max-age=31536000, immutable")
}
