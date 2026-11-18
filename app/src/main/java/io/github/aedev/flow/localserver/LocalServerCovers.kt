package io.github.aedev.flow.localserver

import java.io.File

// GET /download-cover?f=<name>.jpg: cover saved with a download; app storage is out of reach of another device's browser

/** `DownloadCovers` directory under the app's files. */
internal const val COVER_DIRECTORY = "download_covers"

private const val COVER_ROUTE = "/download-cover?f="
private const val COVER_NAME = """[A-Za-z0-9_-]+\.jpg"""
private val COVER_NAME_ONLY = Regex(COVER_NAME)
private val SAVED_COVER_PATH = Regex("""^(?:file://)?/(?:.*/)?$COVER_DIRECTORY/($COVER_NAME)$""")

/** Same-origin URL for a saved-cover path or `file:` URI; [url] otherwise. */
internal fun coverRoute(url: String): String = SAVED_COVER_PATH.matchEntire(url)?.let { COVER_ROUTE + it.groupValues[1] } ?: url

/** Only a plain `name.jpg` resolves, so no path leaves [COVER_DIRECTORY]. */
internal fun savedCover(
    storage: File,
    name: String?,
): File? = name?.takeIf(COVER_NAME_ONLY::matches)?.let { File(File(storage, COVER_DIRECTORY), it) }?.takeIf { it.isFile }

internal fun RequestContext.handleDownloadCover(
    reply: Reply,
    params: Query,
) {
    val cover = savedCover(dbHelper.appContext.filesDir, params["f"])
    if (cover == null) {
        reply.send(404, "No saved cover.", "text/plain; charset=UTF-8")
        return
    }
    reply.sendBytes(200, cover.readBytes(), "image/jpeg", mapOf("Cache-Control" to "private, max-age=3600"))
}
