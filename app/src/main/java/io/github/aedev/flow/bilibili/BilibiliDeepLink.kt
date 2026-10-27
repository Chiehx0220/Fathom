package io.github.aedev.flow.bilibili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** VIEW URL / shared text → first link (share sheet: title + link, often b23.tv). */
object BilibiliDeepLink {
    private val URL = Regex("""https?://[^\s]+""")
    private val SHORT_HOST = Regex("""^https?://b23\.tv/""", RegexOption.IGNORE_CASE)
    private const val MAX_REDIRECTS = 4

    private val client by lazy {
        OkHttpClient
            .Builder()
            .followRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /** Video, live room or uploader; null otherwise, incl. short link ([resolve]). */
    fun parse(text: String): BilibiliRef? = firstLink(text)?.takeIf(BilibiliRef::isBilibiliUrl)?.let(BilibiliRef::ofLink)

    fun isShortLink(text: String): Boolean = firstLink(text)?.let(SHORT_HOST::containsMatchIn) == true

    /** [parse] after following b23.tv redirect. Blocking network IO. */
    suspend fun resolve(text: String): BilibiliRef? {
        parse(text)?.let { return it }
        var url = firstLink(text)?.takeIf { SHORT_HOST.containsMatchIn(it) } ?: return null
        return withContext(Dispatchers.IO) {
            repeat(MAX_REDIRECTS) {
                val location =
                    runCatching {
                        client
                            .newCall(
                                Request
                                    .Builder()
                                    .url(url)
                                    .header("User-Agent", "Mozilla/5.0")
                                    .build(),
                            ).execute()
                            .use { it.header("Location") }
                    }.getOrNull() ?: return@withContext null
                url = location
                parse(url)?.let { return@withContext it }
            }
            null
        }
    }

    private fun firstLink(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ')', ']', '”')
}
