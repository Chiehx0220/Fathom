package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

/** Media/channel data via extractors, shared by handlers. */
object LocalServerMedia {
    fun getVideoId(url: String?): String {
        if (url == null) return ""
        if (url.contains("v=")) {
            val start = url.indexOf("v=") + 2
            val end = url.indexOf("&", start)
            return if (end == -1) url.substring(start) else url.substring(start, end)
        }
        if (url.contains("/shorts/")) {
            val start = url.indexOf("/shorts/") + 8
            val end = url.indexOf("?", start)
            return if (end == -1) url.substring(start) else url.substring(start, end)
        }
        if (url.contains("youtu.be/")) {
            val start = url.indexOf("youtu.be/") + 9
            val end = url.indexOf("?", start)
            return if (end == -1) url.substring(start) else url.substring(start, end)
        }
        // Bilibili video/live room stored as "BV1xx?p=1" / "live:123" (app history ids); a whole link → second row, link inside link.
        return io.github.aedev.flow.bilibili.BilibiliVideoId
            .fromUrl(url)
            ?: io.github.aedev.flow.bilibili.BilibiliLiveId
                .fromUrl(url)
            ?: url
    }

    /** Service owning [url]; YouTube id if none. */
    internal fun serviceIdOfUrl(url: String): Int = runCatching { NewPipe.getServiceByUrl(url).serviceId }.getOrDefault(0)

    internal fun fetchChannelUploads(channelUrl: String): List<InfoItem> =
        LocalServerYouTubeChannel.uploads(channelUrl) ?: fetchChannelUploads(NewPipe.getServiceByUrl(channelUrl), channelUrl)

    internal fun fetchChannelUploads(
        service: StreamingService,
        channelUrl: String,
    ): List<InfoItem> {
        try {
            val channelExtractor = service.getChannelExtractor(channelUrl)
            channelExtractor.fetchPage()
            val tabExtractor = service.getChannelTabExtractor(resolveChannelTabHandler(channelExtractor, "videos"))
            tabExtractor.fetchPage()
            val pageItems: List<*>? = if (tabExtractor.initialPage != null) tabExtractor.initialPage.items else null
            val list = ArrayList<InfoItem>()
            if (pageItems != null) {
                for (item in pageItems) {
                    if (item is InfoItem) {
                        list.add(item)
                    }
                }
            }
            backfillUploaderUrl(list, channelUrl)
            return list
        } catch (e: Exception) {
            serverLog("Failed to fetch uploads for channel $channelUrl: " + e.message)
            return ArrayList()
        }
    }

    // Backfill uploader link: empty → channel route 404; items belong to channelUrl.
    internal fun backfillUploaderUrl(
        items: List<InfoItem>,
        channelUrl: String,
    ) {
        for (item in items) {
            if (item is StreamInfoItem) {
                val currentUploaderUrl = item.uploaderUrl
                if (currentUploaderUrl == null || currentUploaderUrl.trim().isEmpty()) {
                    item.setUploaderUrl(channelUrl)
                }
            }
        }
    }

    // Channel handler for [tab] ("videos", "playlists", ...), else first tab.
    internal fun resolveChannelTabHandler(
        channelExtractor: ChannelExtractor,
        tab: String,
    ): ListLinkHandler =
        channelExtractor.tabs.firstOrNull { handler -> handler.contentFilters.contains(tab) }
            ?: channelExtractor.tabs.firstOrNull()
            ?: throw ExtractionException("Channel exposes no tabs: ${channelExtractor.url}")

    // Resume from nextPage token, else first page. getPage() reads only the token: fresh extractor can resume.
    internal fun <R : InfoItem> fetchInitialOrPage(
        extractor: ListExtractor<R>,
        nextPage: Page?,
    ): InfoItemsPage<R> {
        extractor.fetchPage()
        return if (nextPage != null) extractor.getPage(nextPage) else extractor.initialPage
    }

    const val SERVICE_YOUTUBE = 0
    val SUPPORTED_SERVICE_IDS = intArrayOf(SERVICE_YOUTUBE, BILIBILI_SERVICE_ID)

    fun isSupportedService(serviceId: Int): Boolean {
        for (id in SUPPORTED_SERVICE_IDS) {
            if (id == serviceId) {
                return true
            }
        }
        return false
    }

    @Throws(ExtractionException::class)
    fun getDefaultSearchExtractor(
        service: StreamingService,
        query: String,
    ): SearchExtractor = service.getSearchExtractor(query)

    /**
     * Normalizes audio codec for DASH manifest. Bilibili's bare `"mp4a"` is invalid RFC 6381 (fails `MediaSource.isTypeSupported()`,
     * drops the audio Representation) → AAC-LC (`mp4a.40.2`). Qualified YouTube strings unchanged.
     */
    fun normalizeAudioCodec(codec: String?): String {
        if (codec == null || codec.trim().isEmpty()) {
            return "mp4a.40.2"
        }
        val trimmed = codec.trim()
        if (trimmed == "mp4a") {
            return "mp4a.40.2"
        }
        return trimmed
    }

    fun isOriginalAudioTrack(stream: AudioStream): Boolean =
        stream.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL

    // Default audio-track priority: original, device locale, English, highest bitrate. Shared: stream proxy, manifest, track picker.
    fun audioTrackPriorityComparator(): Comparator<AudioStream> {
        val langCode = Locale.getDefault().language
        return Comparator { a, b ->
            val isOrigA = isOriginalAudioTrack(a)
            val isOrigB = isOriginalAudioTrack(b)
            if (isOrigA != isOrigB) {
                return@Comparator if (isOrigA) -1 else 1
            }
            val localeA = a.audioLocale?.language
            val localeB = b.audioLocale?.language
            val langMatchA = (localeA != null && localeA.equals(langCode, ignoreCase = true))
            val langMatchB = (localeB != null && localeB.equals(langCode, ignoreCase = true))
            if (langMatchA != langMatchB) {
                return@Comparator if (langMatchA) -1 else 1
            }
            val engMatchA = (localeA != null && localeA.equals("en", ignoreCase = true))
            val engMatchB = (localeB != null && localeB.equals("en", ignoreCase = true))
            if (engMatchA != engMatchB) {
                return@Comparator if (engMatchA) -1 else 1
            }
            val brA = if (a.averageBitrate > 0) a.averageBitrate else a.bitrate
            val brB = if (b.averageBitrate > 0) b.averageBitrate else b.bitrate
            brB.toLong().compareTo(brA.toLong())
        }
    }

    fun getResolutionHeight(resolution: String?): Int {
        if (resolution == null || resolution.isEmpty()) return 0
        try {
            // Split by 'p' ("720p60" -> "720"): ignore frame rate
            val parts = resolution.split(Regex("(?i)p"))
            if (parts.isNotEmpty()) {
                val numeric = parts[0].replace(Regex("[^0-9]"), "")
                return if (numeric.isEmpty()) 0 else numeric.toInt()
            }
        } catch (e: Exception) {
        }
        return 0
    }

    // One extractor + page fetch per video, first handler wins.
    fun getCachedExtractor(
        service: StreamingService,
        serviceId: Int,
        mediaUrl: String,
    ): StreamExtractor {
        val key = serviceId.toString() + "_" + mediaUrl
        val cached = LocalServerCaches.extractorCache.get(key)
        if (cached != null) {
            return cached
        }
        val extractor = service.getStreamExtractor(mediaUrl)
        extractor.fetchPage()
        LocalServerCaches.extractorCache.put(key, extractor, 3600000)
        return extractor
    }
}
