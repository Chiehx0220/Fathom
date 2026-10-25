package io.github.aedev.flow.localserver

import android.graphics.Bitmap
import io.github.aedev.flow.platform.LauncherIconArt
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream

/** `ICON_PATH`: the active launcher icon as PNG; revalidated on every load. */
internal fun RequestContext.handleAppIcon(reply: Reply) {
    val png =
        ByteArrayOutputStream().use { out ->
            runBlocking { LauncherIconArt.renderActive(dbHelper.appContext) }.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    reply.sendBytes(200, png, "image/png", mapOf("Cache-Control" to "no-cache"))
}
