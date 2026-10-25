package io.github.aedev.flow.localserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.Page
import java.util.Base64

private const val MAX_TOKEN_CHARS = 65_536

@Serializable
private class PageFields(
    val url: String? = null,
    val id: String? = null,
    val ids: List<String>? = null,
    val cookies: Map<String, String>? = null,
    val body: String? = null,
)

/** Next-page cursor: base64url JSON of [Page] fields; anything else decodes to null. */
internal object PageToken {
    private val json = Json { explicitNulls = false }
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(page: Page?): String? =
        page?.let {
            val fields = PageFields(it.url, it.id, it.ids, it.cookies, it.body?.let(encoder::encodeToString))
            encoder.encodeToString(json.encodeToString(fields).toByteArray())
        }

    fun decode(token: String?): Page? {
        if (token.isNullOrEmpty() || token.length > MAX_TOKEN_CHARS) return null
        return runCatching {
            val fields = json.decodeFromString<PageFields>(String(decoder.decode(token)))
            Page(fields.url, fields.id, fields.ids, fields.cookies, fields.body?.let(decoder::decode))
        }.getOrNull()
    }
}
