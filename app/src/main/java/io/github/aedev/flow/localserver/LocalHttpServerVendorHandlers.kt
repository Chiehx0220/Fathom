package io.github.aedev.flow.localserver

import java.io.FileNotFoundException
import java.io.OutputStream

// The third-party files the web page loads from this device (assets/web/vendor), such as the Video.js player.

private val VENDOR_PATH = Regex("""/vendor/[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*""")

/** The asset path of a /vendor/ request, or null for anything that could leave the vendor folder or is not a plain file name. */
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
 * One text file of the vendor folder. The names of the chunks Video.js loads carry a hash of their content, and its entry
 * files are asked for with the page's version in the address, so every answer can be kept forever.
 */
@Throws(Exception::class)
internal fun ClientHandler.handleVendorAsset(
    os: OutputStream,
    path: String,
) {
    val asset = vendorAssetPath(path)
    val type = vendorContentType(path)
    if (asset == null || type == null) {
        sendResponse(os, 404, "Page Not Found", "text/plain; charset=UTF-8")
        return
    }
    val text =
        try {
            dbHelper.appContext.assets
                .open(asset)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (e: FileNotFoundException) {
            sendResponse(os, 404, "Page Not Found", "text/plain; charset=UTF-8")
            return
        }
    sendResponse(os, 200, text, type, "public, max-age=31536000, immutable")
}
