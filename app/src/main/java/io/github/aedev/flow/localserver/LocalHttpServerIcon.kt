package io.github.aedev.flow.localserver

import android.graphics.Bitmap
import io.github.aedev.flow.platform.LauncherIconArt
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/** `ICON_PATH`: the active launcher icon as PNG; revalidated on every load. */
@Throws(Exception::class)
internal fun ClientHandler.handleAppIcon(os: OutputStream) {
    val png =
        ByteArrayOutputStream().use { out ->
            runBlocking { LauncherIconArt.renderActive(dbHelper.appContext) }.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    val head =
        "HTTP/1.1 200 OK\r\n" +
            "Content-Type: image/png\r\n" +
            "Cache-Control: no-cache\r\n" +
            "Content-Length: ${png.size}\r\n" +
            "Connection: close\r\n\r\n"
    os.write(head.toByteArray(Charsets.UTF_8))
    os.write(png)
    os.flush()
}
