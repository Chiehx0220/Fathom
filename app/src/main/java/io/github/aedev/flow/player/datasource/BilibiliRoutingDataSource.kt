package io.github.aedev.flow.player.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource

/**
 * The data source [YouTubeHttpDataSource.Factory] hands out: Bilibili CDN URLs go to a
 * [BilibiliHttpDataSource], everything else to [youTube], which is upstream's own class untouched.
 * It is chosen per [open], so one instance can serve a YouTube range and then a Bilibili one.
 */
@UnstableApi
internal class BilibiliRoutingDataSource(
    private val youTube: HttpDataSource,
    private val newBilibiliSource: () -> HttpDataSource,
) : BaseDataSource(true),
    HttpDataSource {
    private var active: HttpDataSource? = null

    @UnstableApi
    override fun open(dataSpec: DataSpec): Long {
        val source =
            if (BilibiliHttpSupport.isBilibiliCdnUri(dataSpec.uri)) {
                newBilibiliSource()
            } else {
                youTube
            }
        active = source
        return source.open(dataSpec)
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun close() {
        active?.close()
        active = null
    }

    override fun getUri(): Uri? = active?.uri

    override fun getResponseCode(): Int = active?.responseCode ?: -1

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun clearAllRequestProperties() {}

    override fun clearRequestProperty(name: String) {}

    override fun setRequestProperty(
        name: String,
        value: String,
    ) {}

    companion object {
        /** [youTube] for everything but Bilibili's CDN, which gets a [BilibiliHttpDataSource] carrying [requestProperties]. */
        fun around(
            youTube: HttpDataSource,
            requestProperties: Map<String, String>,
        ): HttpDataSource = BilibiliRoutingDataSource(youTube) { BilibiliHttpDataSource(requestProperties) }
    }
}
