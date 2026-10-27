package io.github.aedev.flow.localserver

import java.io.FileNotFoundException

// App shell, CSS, JS, fonts; third-party files from assets/web/vendor (e.g. Video.js).

private val VENDOR_PATH = Regex("""/vendor/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*""")
private const val IMMUTABLE = "public, max-age=31536000, immutable"
private val FONT_PATH = Regex("""/fonts/[A-Za-z0-9._-]+\.woff2""")

/** /vendor/ asset path; null if outside vendor folder or not a plain file name. */
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

private fun Reply.notFound() = send(404, "Page Not Found", "text/plain; charset=UTF-8")

private fun RequestContext.assetBytes(asset: String): ByteArray? =
    try {
        dbHelper.appContext.assets
            .open(asset)
            .use { it.readBytes() }
    } catch (e: FileNotFoundException) {
        null
    }

/** Vendor text file; Video.js chunk names are content-hashed, entry files carry page version: cacheable forever. */
internal fun RequestContext.handleVendorAsset(
    reply: Reply,
    path: String,
) {
    val asset = vendorAssetPath(path)
    val type = vendorContentType(path)
    val bytes = if (asset != null && type != null) assetBytes(asset) else null
    if (bytes == null || type == null) {
        reply.notFound()
        return
    }
    reply.send(200, bytes.toString(Charsets.UTF_8), type, IMMUTABLE)
}

/** /fonts/ asset path; null unless a plain `.woff2` file name. */
internal fun fontAssetPath(path: String): String? = if (FONT_PATH.matches(path) && !path.contains("..")) "web/app$path" else null

/** App font: file names carry a content hash, so cacheable forever and kept across app updates. */
internal fun RequestContext.handleAppFont(
    reply: Reply,
    path: String,
) {
    val bytes = fontAssetPath(path)?.let { assetBytes(it) }
    if (bytes == null) {
        reply.notFound()
        return
    }
    reply.sendBytes(200, bytes, "font/woff2", mapOf("Cache-Control" to IMMUTABLE))
}

// SPA shell, CSS, JS. ?v= ignored: content change changes WebAssets.appVersion, so max-age=31536000 immutable is safe.
internal fun RequestContext.handleAppShell(reply: Reply) {
    reply.send(200, WebShell.appPage(), "text/html; charset=UTF-8")
}

internal fun RequestContext.handleAppCss(reply: Reply) {
    reply.send(200, WebAssets.appCss, "text/css; charset=UTF-8", IMMUTABLE)
}

internal fun RequestContext.handleAppJs(reply: Reply) {
    reply.send(200, WebAssets.appJs, "application/javascript; charset=UTF-8", IMMUTABLE)
}
