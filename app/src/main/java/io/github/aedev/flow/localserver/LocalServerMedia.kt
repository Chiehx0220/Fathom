package io.github.aedev.flow.localserver

import io.github.aedev.flow.bilibili.BILIBILI_SERVICE_ID
import io.github.aedev.flow.bilibili.BilibiliRef
import io.github.aedev.flow.content.InfoItem
import io.github.aedev.flow.content.Page
import io.github.aedev.flow.content.StreamInfoItem
import io.github.aedev.flow.player.stream.model.AudioStream
import io.github.aedev.flow.player.stream.model.AudioTrackType
import java.util.Locale

/** Media ids and helpers shared by the handlers. */
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
        // Stored as "BV1xx?p=1" / "live:123" (app history ids); whole link → second row.
        return BilibiliRef.Video
            .ofUrl(url)
            ?.id
            ?: BilibiliRef.Live
                .ofUrl(url)
                ?.id
            ?: url
    }

    /** Service owning [url]; YouTube id if none. */
    internal fun serviceIdOfUrl(url: String): Int = if (BilibiliRef.isBilibiliUrl(url)) BILIBILI_SERVICE_ID else SERVICE_YOUTUBE

    /** Uploads of a YouTube channel, newest first; empty when InnerTube gave none. */
    internal fun fetchChannelUploads(channelUrl: String): List<InfoItem> = LocalServerYouTubeChannel.uploads(channelUrl).orEmpty()

    // Backfill uploader link: empty → channel route 404; items belong to channelUrl.
    internal fun backfillUploaderUrl(
        items: List<InfoItem>,
        channelUrl: String,
    ) {
        for (item in items) {
            if (item is StreamInfoItem) {
                val currentUploaderUrl = item.uploaderUrl
                if (currentUploaderUrl == null || currentUploaderUrl.trim().isEmpty()) {
                    item.uploaderUrl = channelUrl
                }
            }
        }
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

    /** DASH audio codec: bare `"mp4a"` is invalid RFC 6381 (Representation dropped) → `mp4a.40.2`. */
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

    fun isOriginalAudioTrack(stream: AudioStream): Boolean = stream.audioTrackType == AudioTrackType.ORIGINAL

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

    /** Height of "720p60"-style [resolution]; frame rate ignored, 0 if unparseable. */
    fun getResolutionHeight(resolution: String?): Int =
        resolution
            .orEmpty()
            .split(Regex("(?i)p"))
            .first()
            .filter { it in '0'..'9' }
            .toIntOrNull() ?: 0
}
