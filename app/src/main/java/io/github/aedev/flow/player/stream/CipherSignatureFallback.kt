package io.github.aedev.flow.player.stream

import android.util.Log
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString

/** `signatureCipher` resolution through the player script's own function (WebView) when NewPipeExtractor fails. */
internal object CipherSignatureFallback {
    private const val TAG = "CipherSignatureFallback"
    private const val SKIP_NEWPIPE_MS = 30 * 60 * 1000L

    @Volatile private var newPipeBrokenAt = 0L

    /** [viaNewPipe] first; after it failed where the player function succeeded, the player function alone for [SKIP_NEWPIPE_MS]. */
    suspend fun streamUrl(
        format: PlayerResponse.StreamingData.Format,
        viaNewPipe: () -> String?,
        viaPlayer: suspend (PlayerResponse.StreamingData.Format) -> String? = ::decipherInPlayer,
        nowMs: () -> Long = System::currentTimeMillis,
    ): String? {
        val now = nowMs()
        if (now - newPipeBrokenAt >= SKIP_NEWPIPE_MS) {
            viaNewPipe()?.let { return it }
            return viaPlayer(format)?.also { newPipeBrokenAt = now }
        }
        return viaPlayer(format)
    }

    internal fun reset() {
        newPipeBrokenAt = 0L
    }

    private suspend fun decipherInPlayer(format: PlayerResponse.StreamingData.Format): String? {
        val params = parseQueryString(format.signatureCipher ?: format.cipher ?: return null)
        val sig = params["s"] ?: return null
        val url = params["url"]?.let { URLBuilder(it) } ?: return null
        val deciphered = CipherDeobfuscator.deobfuscateSignature(sig)
        if (deciphered == null) {
            Log.w(TAG, "no signature for itag=${format.itag}")
            return null
        }
        url.parameters[params["sp"] ?: "signature"] = deciphered
        return url.buildString()
    }
}
