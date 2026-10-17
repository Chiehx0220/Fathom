package io.github.aedev.flow.bilibili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** External Bilibili link target. */
sealed interface BilibiliLinkTarget {
    /** [videoId] form: "BV1xx?p=2" or "live:<roomId>". */
    data class Video(
        val videoId: String,
    ) : BilibiliLinkTarget

    data class Uploader(
        val mid: Long,
    ) : BilibiliLinkTarget
}

/** VIEW intent URL / shared text → link; first link wins (share sheet: title + link, often b23.tv). */
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
    fun parse(text: String): BilibiliLinkTarget? {
        val url = firstLink(text) ?: return null
        if (!BilibiliLink.isBilibili(url)) return null
        BilibiliLiveId.fromUrl(url)?.let { return BilibiliLinkTarget.Video(it) }
        BilibiliVideoId.fromUrl(url)?.let { id ->
            val (bvid, page) = BilibiliVideoId.parse(id)
            return BilibiliLinkTarget.Video(BilibiliVideoId.of(bvid, page))
        }
        return BilibiliChannelId.midOf(url)?.let { BilibiliLinkTarget.Uploader(it) }
    }

    fun isShortLink(text: String): Boolean = firstLink(text)?.let(SHORT_HOST::containsMatchIn) == true

    /** [parse] after following b23.tv redirect. Blocking network IO. */
    suspend fun resolve(text: String): BilibiliLinkTarget? {
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
